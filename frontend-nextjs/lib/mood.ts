"use client";

import { apiFetch } from "./api";
import type { MoodHistoryEntry, MoodInsight, MoodType } from "./types";

/** Dashboard mood REST client: history, check-in, insight. */

export function getMoodHistory(days = 7): Promise<MoodHistoryEntry[]> {
  const params = new URLSearchParams({ days: String(days) });
  return apiFetch<MoodHistoryEntry[]>(`/mood/history?${params.toString()}`);
}

export function checkinMood(mood: MoodType, note?: string): Promise<MoodHistoryEntry> {
  return apiFetch<MoodHistoryEntry>("/mood/checkin", {
    method: "POST",
    body: JSON.stringify({ mood, note: note ?? null }),
  });
}

export function getMoodInsight(): Promise<MoodInsight> {
  return apiFetch<MoodInsight>("/mood/insight");
}
