"use client";

import { ApiError, apiFetch } from "./api";
import type { AvatarStreamResponse, MentorProfile } from "./types";

/** Mentor + avatar REST client. The D-ID key never appears here by design. */

export async function getMyMentor(): Promise<MentorProfile | null> {
  try {
    return await apiFetch<MentorProfile | null>("/mentor/my-mentor");
  } catch (err) {
    if (err instanceof ApiError && err.status === 404) return null;
    throw err;
  }
}

export function uploadAvatar(file: File, mentorName: string, voiceId?: string): Promise<MentorProfile> {
  const form = new FormData();
  form.append("file", file);
  form.append("mentorName", mentorName);
  if (voiceId) form.append("voiceId", voiceId);
  // json:false — the browser sets the multipart boundary itself.
  return apiFetch<MentorProfile>("/mentor/upload-avatar", { method: "POST", body: form }, { json: false });
}

export function createAvatarStreamRequest(): Promise<AvatarStreamResponse> {
  return apiFetch<AvatarStreamResponse>("/mentor/avatar-stream", { method: "POST" });
}

export function submitAvatarAnswer(streamId: string, sessionId: string, sdpAnswer: string): Promise<void> {
  return apiFetch<void>(`/mentor/avatar-stream/${encodeURIComponent(streamId)}/sdp`, {
    method: "POST",
    body: JSON.stringify({ sessionId, sdpAnswer }),
  });
}

export function sendAvatarIce(
  streamId: string,
  sessionId: string,
  candidate: string,
  sdpMid: string | null,
  sdpMLineIndex: number | null
): Promise<void> {
  return apiFetch<void>(`/mentor/avatar-stream/${encodeURIComponent(streamId)}/ice`, {
    method: "POST",
    body: JSON.stringify({ sessionId, candidate, sdpMid, sdpMLineIndex }),
  });
}

export function closeAvatarStream(streamId: string, sessionId: string): Promise<void> {
  const params = new URLSearchParams({ sessionId });
  return apiFetch<void>(
    `/mentor/avatar-stream/${encodeURIComponent(streamId)}?${params.toString()}`,
    { method: "DELETE" }
  );
}
