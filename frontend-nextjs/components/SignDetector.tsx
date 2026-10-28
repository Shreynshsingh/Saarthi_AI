"use client";

import { useEffect, useRef, type RefObject } from "react";
import type { HandLandmarker, NormalizedLandmark } from "@mediapipe/tasks-vision";
import type { SignName } from "@/lib/types";

/**
 * Task 4: sign-language input, camera frames 100% in the browser.
 *
 * MVP SCOPE: 10 STATIC single-hand poses held steady (see
 * sign_classifier.py). No sentence translation, no motion grammar, no
 * two-hand sequences.
 *
 * PRIVACY: landmarks are numbers only. This component reads pixels from the
 * shared preview <video> element, runs an on-device HandLandmarker, draws
 * the skeleton on a local canvas, and emits only the 21 landmark triples
 * via onSignDetected. It never captures, uploads, or logs video frames,
 * screenshots, or canvas pixels - verify in the Network tab.
 *
 * STABILITY (spec: same sign in 3 consecutive detections, conf >= 0.80):
 * the frontend cannot know sign LABELS - classification lives in Python -
 * so "same sign" is measured as the same UNMOVING hand: the last 3 frames
 * (3 x 400ms ~= 1.2s held) must all show a hand whose 21 landmarks each
 * moved less than STEADY_EPS, with handedness confidence >= 0.80 every
 * frame. This is a pure change-detector, not sign rules: no pose is ever
 * mapped to a name here (sign is always null at emit; the server assigns
 * the label). After an emit, a 3s cooldown plus a re-arm rule (a tick with
 * no hand, or a moved hand) keeps one held pose to exactly one message.
 */

const WASM_URL = "https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.35/wasm";
const MODEL_URL =
  "https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task";

const TICK_MS = 400;
const STABLE_FRAMES = 3;
const MIN_CONFIDENCE = 0.8;
const STEADY_EPS = 0.035;
const COOLDOWN_MS = 3000;
const VIDEO_WAIT_MS = 12000;

export type SignDetectorStatus = "loading" | "ready" | "no-camera" | "model-failed";

interface FrameSample {
  landmarks: number[][];
  confidence: number;
}

/** True when every landmark barely moved between all buffered frames. */
function handSteady(buffer: FrameSample[]): boolean {
  for (let i = 1; i < buffer.length; i++) {
    const prev = buffer[i - 1].landmarks;
    const curr = buffer[i].landmarks;
    for (let j = 0; j < 21; j++) {
      if (Math.abs(curr[j][0] - prev[j][0]) > STEADY_EPS) return false;
      if (Math.abs(curr[j][1] - prev[j][1]) > STEADY_EPS) return false;
    }
  }
  return true;
}

export default function SignDetector({
  videoRef,
  enabled,
  onSignDetected,
  onStatusChange,
  onError,
  onPresenceChange,
}: {
  videoRef: RefObject<HTMLVideoElement | null>;
  enabled: boolean;
  onSignDetected: (sign: SignName | null, confidence: number, landmarks: number[][]) => void;
  onStatusChange?: (status: SignDetectorStatus) => void;
  onError?: (message: string | null) => void;
  // Fires only on hand present/absent TRANSITIONS so the session panel can
  // show "Hold it steady..." while a hand is visible but not yet stable.
  onPresenceChange?: (present: boolean) => void;
}) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const landmarkerRef = useRef<HandLandmarker | null>(null);
  const drawRef = useRef<((landmarks: NormalizedLandmark[]) => void) | null>(null);
  const statusRef = useRef<SignDetectorStatus>("loading");
  const callbacksRef = useRef({ onSignDetected, onStatusChange, onError, onPresenceChange });
  callbacksRef.current = { onSignDetected, onStatusChange, onError, onPresenceChange };

  useEffect(() => {
    if (!enabled) return;
    let cancelled = false;
    let intervalId = 0;
    const buffer: FrameSample[] = [];
    let cooldownUntil = 0;
    let armed = true;
    let emittedPose: number[][] | null = null;

    const setStatus = (status: SignDetectorStatus) => {
      if (cancelled) return;
      statusRef.current = status;
      callbacksRef.current.onStatusChange?.(status);
    };

    const clearCanvas = () => {
      const canvas = canvasRef.current;
      const ctx = canvas?.getContext("2d");
      if (canvas && ctx) ctx.clearRect(0, 0, canvas.width, canvas.height);
    };

    const stopLoop = () => {
      window.clearInterval(intervalId);
      clearCanvas();
      callbacksRef.current.onPresenceChange?.(false);
      // NOTE: the model stays cached while mounted, so re-enabling sign mode
      // never reloads it. It is closed only on unmount below.
    };

    /** Waits for the shared video element to become playable. */
    const waitForVideo = async (): Promise<HTMLVideoElement | null> => {
      const deadline = Date.now() + VIDEO_WAIT_MS;
      while (!cancelled && Date.now() < deadline) {
        const video = videoRef.current;
        if (video && video.readyState >= 2 && video.videoWidth > 0) return video;
        await new Promise((resolve) => setTimeout(resolve, 250));
      }
      return null;
    };

    const ensureModel = async (): Promise<boolean> => {
      if (landmarkerRef.current) return true;
      const vision = await import("@mediapipe/tasks-vision");
      if (cancelled) return false;
      const fileset = await vision.FilesetResolver.forVisionTasks(WASM_URL);
      if (cancelled) return false;
      const options = {
        baseOptions: { modelAssetPath: MODEL_URL, delegate: "GPU" as const },
        runningMode: "VIDEO" as const,
        numHands: 1,
        minHandDetectionConfidence: 0.5,
        minHandPresenceConfidence: 0.5,
        minTrackingConfidence: 0.5,
      };
      try {
        landmarkerRef.current = await vision.HandLandmarker.createFromOptions(fileset, options);
      } catch {
        landmarkerRef.current = await vision.HandLandmarker.createFromOptions(fileset, {
          ...options,
          baseOptions: { modelAssetPath: MODEL_URL, delegate: "CPU" as const },
        });
      }
      const canvas = canvasRef.current;
      const ctx = canvas?.getContext("2d");
      if (ctx) {
        const utils = new vision.DrawingUtils(ctx);
        const connections = vision.HandLandmarker.HAND_CONNECTIONS;
        drawRef.current = (landmarks: NormalizedLandmark[]) => {
          utils.drawConnectors(landmarks, connections, { color: "#2A9D8F", lineWidth: 3 });
          utils.drawLandmarks(landmarks, { color: "#2A9D8F", lineWidth: 1, radius: 3 });
        };
      }
      return true;
    };

    const boot = async () => {
      setStatus("loading");
      let wasPresent = false;
      const reportPresence = (present: boolean) => {
        if (present !== wasPresent) {
          wasPresent = present;
          callbacksRef.current.onPresenceChange?.(present);
        }
      };
      const video = await waitForVideo();
      if (!video) {
        setStatus("no-camera");
        return;
      }
      try {
        if (!(await ensureModel())) return;
      } catch {
        if (cancelled) return;
        setStatus("model-failed");
        callbacksRef.current.onError?.(
          "The sign model couldn't load right now — but text and voice work just as well."
        );
        return;
      }
      if (cancelled) return;
      setStatus("ready");

      intervalId = window.setInterval(() => {
        const current = videoRef.current;
        const canvas = canvasRef.current;
        const landmarker = landmarkerRef.current;
        if (!current || !canvas || !landmarker || current.readyState < 2 || current.videoWidth === 0) {
          buffer.length = 0;
          armed = true;
          clearCanvas();
          reportPresence(false);
          return;
        }
        if (canvas.clientWidth > 0 && (canvas.width !== canvas.clientWidth || canvas.height !== canvas.clientHeight)) {
          canvas.width = canvas.clientWidth;
          canvas.height = canvas.clientHeight;
        }
        let hands: NormalizedLandmark[][] = [];
        let confidence = 0;
        try {
          const result = landmarker.detectForVideo(current, performance.now());
          hands = result.landmarks ?? [];
          confidence = result.handedness?.[0]?.[0]?.score ?? 0;
        } catch {
          return;
        }
        const hand = hands[0];
        const ctx = canvas.getContext("2d");
        if (ctx) ctx.clearRect(0, 0, canvas.width, canvas.height);
        if (!hand || hand.length !== 21) {
          buffer.length = 0;
          armed = true;
          emittedPose = null;
          reportPresence(false);
          return;
        }
        reportPresence(true);
        try {
          drawRef.current?.(hand);
        } catch {
          // A drawing glitch must never break detection.
        }
        const landmarks = hand.map((p) => [p.x, p.y, p.z]);
        if (!armed) {
          // Re-arm check: a moved hand (or an absent one, above) re-arms.
          const moved =
            emittedPose === null ||
            landmarks.some(
              (p, i) =>
                Math.abs(p[0] - (emittedPose as number[][])[i][0]) > STEADY_EPS ||
                Math.abs(p[1] - (emittedPose as number[][])[i][1]) > STEADY_EPS
            );
          if (moved) {
            armed = true;
            emittedPose = null;
            buffer.length = 0;
          } else {
            return;
          }
        }
        buffer.push({ landmarks, confidence });
        if (buffer.length > STABLE_FRAMES) buffer.shift();
        if (buffer.length < STABLE_FRAMES) return;
        if (Date.now() < cooldownUntil) return;
        if (!buffer.every((s) => s.confidence >= MIN_CONFIDENCE)) return;
        if (!handSteady(buffer)) return;
        // Stable hold: exactly one emit, then cooldown + disarm.
        buffer.length = 0;
        cooldownUntil = Date.now() + COOLDOWN_MS;
        armed = false;
        emittedPose = landmarks;
        callbacksRef.current.onSignDetected(null, confidence, landmarks);
      }, TICK_MS);
    };

    void boot();
    return () => {
      cancelled = true;
      stopLoop();
    };
  }, [enabled, videoRef]);

  // Model teardown on unmount only - toggling sign mode keeps the cache.
  useEffect(
    () => () => {
      try {
        landmarkerRef.current?.close();
      } catch {
        // already closed
      }
      landmarkerRef.current = null;
      drawRef.current = null;
    },
    []
  );

  return (
    <div className="pointer-events-none absolute inset-0 overflow-hidden rounded-2xl">
      <canvas ref={canvasRef} className="absolute inset-0 h-full w-full -scale-x-100" aria-hidden="true" />
    </div>
  );
}
