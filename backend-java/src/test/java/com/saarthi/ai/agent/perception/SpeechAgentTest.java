package com.saarthi.ai.agent.perception;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Transcription input guards. Pure unit tests - no Groq calls.
 */
class SpeechAgentTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void transcribeGuardsWithoutNetwork() {
        // Null WebClient: these paths return before any HTTP call.
        SpeechAgent agent = new SpeechAgent(null, objectMapper, "whisper-large-v3", 10000L);

        assertEquals("", agent.transcribe(null, "VOICE"));
        assertEquals("", agent.transcribe("   ", "VOICE"));
        assertEquals("", agent.transcribe("!!!not-base64!!!", "VOICE"));
        assertEquals("", agent.transcribe("aGVsbG8=", "TEXT"));
        assertEquals("", agent.transcribe("aGVsbG8=", null));
    }

    @Test
    void parseTranscriptionReadsText() {
        assertEquals("hello there", SpeechAgent.parseTranscription(
                objectMapper, "{\"text\":\"  hello there \"}"));
        assertEquals("", SpeechAgent.parseTranscription(objectMapper, "{\"text\":\"\"}"));
        assertEquals("", SpeechAgent.parseTranscription(objectMapper, "{}"));
        assertEquals("", SpeechAgent.parseTranscription(objectMapper, "not-json"));
        assertEquals("", SpeechAgent.parseTranscription(objectMapper, null));
    }
}
