package com.saarthi.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.UUID;

/**
 * Client -> Server WebSocket payload.
 * Unknown fields (e.g. a spoofed "userId") are ignored: identity always comes
 * from the authenticated session, never the payload.
 * audioBase64 carries Base64 webm/mp3 when inputMode is VOICE (Module 3+).
 * avatarStreamId/avatarSessionId (Module 5+, nullable) identify the caller's
 * live avatar stream so the orchestrator can voice sentences through it; the
 * stream is always ownership-verified server-side before use.
 * landmarks (Module 6+, nullable) carries 21 [x, y, z] hand-landmark
 * triples for SIGN input frames. Only numbers cross the wire - video
 * frames never leave the browser.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatRequest(String type, UUID conversationId, String inputMode, String text, String audioBase64, String emotion, Double emotionConfidence, String signDetected, List<List<Double>> landmarks, String avatarStreamId, String avatarSessionId) {}
