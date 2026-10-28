"use client";

import { useEffect, useRef, useState } from "react";
import { VideoOff } from "lucide-react";
import type { FaceLandmarker } from "@mediapipe/tasks-vision";
import EmotionBadge from "./EmotionBadge";
import type { MoodType } from "@/lib/types";

/**
 * F-06: facial emotion, 100% in the browser.
 *
 * PRIVACY: video frames never leave this component — they are read from the
 * local <video> element by an on-device MediaPipe model, and only the winning
 * emotion label + confidence are emitted via onEmotionChange for ChatRequest.
 *
 * Heuristic over averaged FaceLandmarker blendshapes (rolling mean of the last
 * 5 detections, sampled every 400ms to avoid flicker):
 * - strong mouth smile -> HAPPY
 * - frown together with lowered brow -> SAD
 * - lowered brow + open jaw + widened eyes -> STRESSED
 * - heavy eyelids / weak facial activity sustained ~6s -> TIRED
 * - otherwise -> NEUTRAL
 * Confidence is always the winning blendshape score.
 */

const WASM_URL = "https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.35/wasm";
const MODEL_URL =
  "https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task";

type DetectorStatus = "loading" | "ready" | "camera-off" | "model-failed";

const BLENDSHAPES = [
  "mouthSmileLeft",
  "mouthSmileRight",
  "mouthFrownLeft",
  "mouthFrownRight",
  "browDownLeft",
  "browDownRight",
  "eyeWideLeft",
  "eyeWideRight",
  "eyeBlinkLeft",
  "eyeBlinkRight",
  "jawOpen",
] as const;

function classify(avg: Map<string, number>): { emotion: MoodType; confidence: number; peak: number } {
  const at = (name: string): number => avg.get(name) ?? 0;
  const smile = Math.max(at("mouthSmileLeft"), at("mouthSmileRight"));
  const frown = Math.max(at("mouthFrownLeft"), at("mouthFrownRight"));
  const brow = Math.max(at("browDownLeft"), at("browDownRight"));
  const wide = Math.max(at("eyeWideLeft"), at("eyeWideRight"));
  const jaw = at("jawOpen");
  const peak = Math.max(smile, frown, brow, wide, jaw);

  if (smile > 0.5) return { emotion: "HAPPY", confidence: smile, peak };
  if (frown > 0.4 && brow > 0.35) return { emotion: "SAD", confidence: Math.max(frown, brow), peak };
  if (brow > 0.4 && jaw > 0.35 && wide > 0.4)
    return { emotion: "STRESSED", confidence: Math.max(brow, jaw, wide), peak };
  return { emotion: "NEUTRAL", confidence: 0.6, peak };
}

export default function EmotionDetector({
  onEmotionChange,
  onCameraError,
  onVideoReady,
  active = true,
}: {
  onEmotionChange: (emotion: MoodType, confidence: number) => void;
  onCameraError?: (error: string | null) => void;
  // Task 4: shares the preview <video> ELEMENT itself so SignDetector reads
  // pixels from the same element its overlay canvas covers (read-only; both
  // MediaPipe landmarkers can share one element - no second getUserMedia
  // call). Null when the camera is off or this component unmounts.
  onVideoReady?: (video: HTMLVideoElement | null) => void;
  active?: boolean;
}) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const landmarkerRef = useRef<FaceLandmarker | null>(null);
  const callbacksRef = useRef({ onEmotionChange, onCameraError, onVideoReady });
  callbacksRef.current = { onEmotionChange, onCameraError, onVideoReady };
  const [status, setStatus] = useState<DetectorStatus>("loading");
  const [display, setDisplay] = useState<{ emotion: MoodType; confidence: number }>({
    emotion: "NEUTRAL",
    confidence: 0.6,
  });
  const [facePresent, setFacePresent] = useState(false);

  useEffect(() => {
    if (!active) return;
    let cancelled = false;
    let intervalId = 0;
    const history: Array<Map<string, number>> = [];
    let weakStreak = 0;
    let lastEmitted: string | null = null;

    const emit = (emotion: MoodType, confidence: number) => {
      const rounded = Math.round(Math.min(0.99, Math.max(0.4, confidence)) * 100) / 100;
      const key = `${emotion}:${rounded.toFixed(2)}`;
      const prev = lastEmitted;
      lastEmitted = key;
      if (prev === null || prev.split(":")[0] !== emotion || Math.abs(parseFloat(prev.split(":")[1]) - rounded) > 0.08) {
        callbacksRef.current.onEmotionChange(emotion, rounded);
        setDisplay({ emotion, confidence: rounded });
      }
    };

    const stopAll = () => {
      window.clearInterval(intervalId);
      streamRef.current?.getTracks().forEach((track) => track.stop());
      streamRef.current = null;
      callbacksRef.current.onVideoReady?.(null);
      try {
        landmarkerRef.current?.close();
      } catch {
        // already closed
      }
      landmarkerRef.current = null;
    };

    const boot = async () => {
      try {
        const stream = await navigator.mediaDevices.getUserMedia({
          video: { width: { ideal: 640 }, height: { ideal: 480 }, facingMode: "user" },
        });
        if (cancelled) {
          stream.getTracks().forEach((track) => track.stop());
          return;
        }
        streamRef.current = stream;
        const video = videoRef.current;
        if (video) {
          video.srcObject = stream;
          await video.play().catch(() => undefined);
          callbacksRef.current.onVideoReady?.(video);
        }
      } catch {
        if (cancelled) return;
        setStatus("camera-off");
        callbacksRef.current.onVideoReady?.(null);
        callbacksRef.current.onCameraError?.(
          "Camera is off — no problem. You can still talk and type, and I'll assume a neutral mood."
        );
        callbacksRef.current.onEmotionChange("NEUTRAL", 0.6);
        return;
      }
      callbacksRef.current.onCameraError?.(null);

      try {
        const vision = await import("@mediapipe/tasks-vision");
        if (cancelled) return;
        const fileset = await vision.FilesetResolver.forVisionTasks(WASM_URL);
        if (cancelled) return;
        try {
          landmarkerRef.current = await vision.FaceLandmarker.createFromOptions(fileset, {
            baseOptions: { modelAssetPath: MODEL_URL, delegate: "GPU" },
            outputFaceBlendshapes: true,
            runningMode: "VIDEO",
            numFaces: 1,
          });
        } catch {
          landmarkerRef.current = await vision.FaceLandmarker.createFromOptions(fileset, {
            baseOptions: { modelAssetPath: MODEL_URL, delegate: "CPU" },
            outputFaceBlendshapes: true,
            runningMode: "VIDEO",
            numFaces: 1,
          });
        }
      } catch {
        if (cancelled) return;
        setStatus("model-failed");
        callbacksRef.current.onEmotionChange("NEUTRAL", 0.6);
        return;
      }
      if (cancelled) return;
      setStatus("ready");

      intervalId = window.setInterval(() => {
        const video = videoRef.current;
        const landmarker = landmarkerRef.current;
        if (!video || !landmarker || video.readyState < 2 || video.videoWidth === 0) return;
        let shapes: Array<{ categoryName: string; score: number }> | undefined;
        try {
          shapes = landmarker.detectForVideo(video, performance.now()).faceBlendshapes?.[0]?.categories;
        } catch {
          return;
        }
        setFacePresent((prev) => {
          const next = shapes !== undefined && shapes.length > 0;
          return prev === next ? prev : next;
        });
        if (!shapes || shapes.length === 0) return;
        const frame = new Map<string, number>();
        for (const name of BLENDSHAPES) frame.set(name, 0);
        for (const c of shapes) {
          if (frame.has(c.categoryName)) frame.set(c.categoryName, c.score);
        }
        history.push(frame);
        if (history.length > 5) history.shift();
        const avg = new Map<string, number>();
        for (const name of BLENDSHAPES) {
          let sum = 0;
          for (const past of history) sum += past.get(name) ?? 0;
          avg.set(name, sum / history.length);
        }
        const { emotion, confidence, peak } = classify(avg);
        const blink = Math.max(avg.get("eyeBlinkLeft") ?? 0, avg.get("eyeBlinkRight") ?? 0);
        if (emotion !== "NEUTRAL") {
          weakStreak = 0;
          emit(emotion, confidence);
        } else if (blink > 0.45 || peak < 0.25) {
          weakStreak += 1;
          if (weakStreak >= 15) emit("TIRED", 0.55);
          else emit("NEUTRAL", 0.6);
        } else {
          weakStreak = 0;
          emit("NEUTRAL", 0.6);
        }
      }, 400);
    };

    void boot();
    return () => {
      cancelled = true;
      stopAll();
    };
  }, [active]);

  return (
    <div className="relative h-32 w-44 shrink-0 overflow-hidden rounded-2xl bg-black/40 ring-1 ring-white/10 md:h-36 md:w-52">
      {status === "camera-off" ? (
        <div className="flex h-full w-full flex-col items-center justify-center gap-1 px-2 text-center text-white/60">
          <VideoOff className="h-6 w-6" aria-hidden="true" />
          <span className="text-xs">Camera off — text mode works fully</span>
        </div>
      ) : (
        <video
          ref={videoRef}
          muted
          playsInline
          className="h-full w-full -scale-x-100 object-cover"
          aria-label="Your camera preview. Video stays in this browser and is never recorded."
        />
      )}
      {status === "model-failed" ? (
        <span className="absolute inset-x-1.5 bottom-1.5 rounded-full bg-black/60 px-2 py-1 text-center text-[11px] text-white/85">
          Emotion unavailable - you can still talk.
        </span>
      ) : (
        status !== "camera-off" && (
          <span className="absolute inset-x-1.5 bottom-1.5 flex justify-center">
            <EmotionBadge
              emotion={display.emotion}
              confidence={display.confidence}
              live={status === "ready" && facePresent}
            />
          </span>
        )
      )}
    </div>
  );
}
