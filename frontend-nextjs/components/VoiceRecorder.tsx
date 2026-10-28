"use client";

import { useEffect, useRef, useState } from "react";
import { Mic } from "lucide-react";

/**
 * Push-to-talk mic control. Click to start, click again to stop; the recorded
 * webm Blob is converted to a bare base64 payload for ChatRequest.audioBase64.
 * Everything network-bound happens in the session page — this component only
 * records and reports.
 */
export default function VoiceRecorder({
  onAudioReady,
  onError,
  onRecordingChange,
  disabled = false,
  maxSeconds = 15,
}: {
  onAudioReady: (base64: string) => void;
  onError: (message: string) => void;
  onRecordingChange?: (recording: boolean) => void;
  disabled?: boolean;
  maxSeconds?: number;
}) {
  const [recording, setRecording] = useState(false);
  const [secs, setSecs] = useState(0);
  const recorderRef = useRef<MediaRecorder | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const partsRef = useRef<BlobPart[]>([]);
  const timerRef = useRef<number | null>(null);
  const stopTimerRef = useRef<number | null>(null);
  const callbacksRef = useRef({ onAudioReady, onError, onRecordingChange });
  callbacksRef.current = { onAudioReady, onError, onRecordingChange };

  const clearTimers = () => {
    if (timerRef.current !== null) window.clearInterval(timerRef.current);
    if (stopTimerRef.current !== null) window.clearTimeout(stopTimerRef.current);
    timerRef.current = null;
    stopTimerRef.current = null;
  };

  const stopTracks = () => {
    streamRef.current?.getTracks().forEach((track) => track.stop());
    streamRef.current = null;
  };

  const setRecordingState = (value: boolean) => {
    setRecording(value);
    callbacksRef.current.onRecordingChange?.(value);
  };

  const stop = () => {
    const recorder = recorderRef.current;
    if (!recorder || recorder.state === "inactive") {
      clearTimers();
      stopTracks();
      setRecordingState(false);
      return;
    }
    recorder.onstop = () => {
      clearTimers();
      const blob = new Blob(partsRef.current, { type: recorder.mimeType || "audio/webm" });
      partsRef.current = [];
      recorderRef.current = null;
      stopTracks();
      setRecordingState(false);
      if (blob.size === 0) {
        callbacksRef.current.onError("I couldn't hear that clearly. Take your time, I'm here.");
        return;
      }
      const reader = new FileReader();
      reader.onloadend = () => {
        const url = typeof reader.result === "string" ? reader.result : "";
        const base64 = url.includes(",") ? url.split(",", 2)[1] : "";
        if (!base64) {
          callbacksRef.current.onError("I couldn't hear that clearly. Take your time, I'm here.");
        } else {
          callbacksRef.current.onAudioReady(base64);
        }
      };
      reader.onerror = () =>
        callbacksRef.current.onError("I couldn't hear that clearly. Take your time, I'm here.");
      reader.readAsDataURL(blob);
    };
    recorder.stop();
  };

  const start = async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      streamRef.current = stream;
      partsRef.current = [];
      const mimeType =
        typeof MediaRecorder !== "undefined" && MediaRecorder.isTypeSupported("audio/webm;codecs=opus")
          ? "audio/webm;codecs=opus"
          : "";
      const recorder = mimeType ? new MediaRecorder(stream, { mimeType }) : new MediaRecorder(stream);
      recorder.ondataavailable = (event: BlobEvent) => {
        if (event.data.size > 0) partsRef.current.push(event.data);
      };
      recorderRef.current = recorder;
      recorder.start();
      setSecs(0);
      setRecordingState(true);
      timerRef.current = window.setInterval(() => setSecs((s) => s + 1), 1000);
      stopTimerRef.current = window.setTimeout(() => stop(), maxSeconds * 1000);
    } catch (err) {
      stopTracks();
      const denied =
        err instanceof DOMException && (err.name === "NotAllowedError" || err.name === "SecurityError");
      callbacksRef.current.onError(
        denied
          ? "The mic is blocked, so I can't hear you — but text mode works just as well."
          : "I couldn't start the mic. Please try again, or type instead."
      );
    }
  };

  const toggle = () => {
    if (disabled && !recording) return;
    if (recording) stop();
    else void start();
  };

  const recordingRef = useRef(recording);
  recordingRef.current = recording;

  // Mode switch (e.g. voice -> sign) while recording: discard the partial
  // take cleanly WITHOUT sending it. Normal stop-then-send already cleared
  // `recording` before the parent disables us, so it never trips this.
  useEffect(() => {
    if (!disabled || !recordingRef.current) return;
    const recorder = recorderRef.current;
    if (recorder) recorder.onstop = null;
    try {
      recorder?.stop();
    } catch {
      // already stopped
    }
    partsRef.current = [];
    clearTimers();
    stopTracks();
    setRecordingState(false);
  }, [disabled]);

  useEffect(
    () => () => {
      clearTimers();
      try {
        recorderRef.current?.stop();
      } catch {
        // already stopped
      }
      stopTracks();
    },
    []
  );

  return (
    <span className="flex flex-col items-center gap-1">
      <button
        type="button"
        onClick={toggle}
        disabled={disabled && !recording}
        aria-label={recording ? "Stop recording and send" : "Start voice recording"}
        aria-pressed={recording}
        className={`flex h-16 w-16 items-center justify-center rounded-full shadow-lg transition disabled:opacity-50 ${
          recording ? "bg-primary mic-listening" : "bg-primary hover:bg-primaryDark"
        }`}
      >
        <Mic className="h-6 w-6" aria-hidden="true" />
      </button>
      <span className="text-center text-[11px] leading-tight text-white/60" aria-live="polite">
        {recording ? `Listening… ${secs}s` : "Tap to talk"}
      </span>
    </span>
  );
}
