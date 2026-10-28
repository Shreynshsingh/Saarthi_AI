package com.saarthi.ai.dto;

import java.util.UUID;

/**
 * Server -> Client payload. MENTOR_RESPONSE_CHUNK carries textChunk plus
 * audioBase64 for that complete sentence; TRANSCRIPTION carries transcribedText.
 * avatarSpeaking is true only when the live avatar accepted voicing that exact
 * sentence — the frontend then mutes its own audio playback for the chunk
 * because the avatar video stream produces the sound (no double audio).
 * It is false on every other frame.
 * A confident SIGN input (Module 6+) first emits TRANSCRIPTION with
 * transcribedText "[Sign: HELP] I need help", then streams the mentor reply.
 * JSON field names: type, textChunk, transcribedText, audioBase64,
 * conversationId, isFinal, avatarSpeaking.
 */
public record ChatResponse(String type, String textChunk, String transcribedText, String audioBase64, UUID conversationId, Boolean isFinal, Boolean avatarSpeaking) {}
