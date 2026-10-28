package com.saarthi.ai.dto;

import java.util.UUID;

/**
 * Universal context passed from agent to agent inside the orchestrator.
 * No agent needs to know about the others - everything flows through this record.
 */
public record AgentContext(
        UUID conversationId,
        UUID userId,
        String textInput,       // Resolved user text (TEXT mode, or Whisper output for VOICE)
        String signInput,       // From Sign Agent, e.g. "HELP" (Module 5+)
        String emotion,         // From Emotion Agent, e.g. "STRESSED"
        Double emotionConfidence,
        String inputMode,       // TEXT / VOICE / SIGN
        String audioBase64      // Raw client audio for VOICE input (Module 3+)
) {
}
