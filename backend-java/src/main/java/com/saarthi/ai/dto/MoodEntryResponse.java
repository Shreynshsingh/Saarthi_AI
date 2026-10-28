package com.saarthi.ai.dto;

import java.util.UUID;

/**
 * One mood point for the dashboard chart. Timestamps travel as ISO strings
 * so the wire format never depends on Jackson date configuration.
 */
public record MoodEntryResponse(UUID id, String mood, Float confidence, String note, String loggedAt,
                                UUID conversationId) {
}
