"use client";

import { apiFetch } from "./api";
import type { ConversationSummary } from "./types";

/** Dashboard memory REST client: recent conversation summaries only. */

export function getRecentConversations(limit = 10): Promise<ConversationSummary[]> {
  const params = new URLSearchParams({ limit: String(limit) });
  return apiFetch<ConversationSummary[]>(`/conversations?${params.toString()}`);
}
