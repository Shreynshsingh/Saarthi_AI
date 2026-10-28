package com.saarthi.ai.dto;

import java.util.UUID;

/**
 * Dashboard "recent conversations" row. Preview is the latest message,
 * truncated server-side - the dashboard never pulls full transcripts.
 */
public record ConversationSummaryResponse(UUID id, String title, String startedAt, String preview) {
}
