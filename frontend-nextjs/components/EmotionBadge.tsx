"use client";

import type { MoodType } from "@/lib/types";

const STYLES: Record<MoodType, string> = {
  HAPPY: "bg-emerald-100 text-emerald-800",
  SAD: "bg-blue-100 text-blue-800",
  STRESSED: "bg-amber-100 text-amber-800",
  ANXIOUS: "bg-orange-100 text-orange-800",
  TIRED: "bg-slate-200 text-slate-700",
  NEUTRAL: "bg-teal-50 text-teal-800",
  ANGRY: "bg-amberWarm/30 text-charcoal",
};

/** Live pill per F-06: "Emotion: STRESSED (87%)". Never red, never alarming. */
export default function EmotionBadge({
  emotion,
  confidence,
  live,
}: {
  emotion: MoodType;
  confidence: number;
  live: boolean;
}) {
  return (
    <span
      role="status"
      aria-live="polite"
      title={live ? "Detected from your camera, privately in this browser" : "Camera off — assuming neutral"}
      className={`inline-flex items-center gap-2 rounded-full px-4 py-1.5 text-sm font-medium ${STYLES[emotion]}`}
    >
      <span aria-hidden="true" className={`h-2 w-2 rounded-full ${live ? "bg-current" : "bg-mist"}`} />
      Emotion: {emotion.charAt(0) + emotion.slice(1).toLowerCase()} {Math.round(confidence * 100)}%
    </span>
  );
}
