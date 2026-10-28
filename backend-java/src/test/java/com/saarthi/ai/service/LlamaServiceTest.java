package com.saarthi.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Groq request shaping + SSE parsing. Pure unit tests - no HTTP calls, no Spring.
 */
class LlamaServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void userContentFollowsExactFormat() {
        assertEquals("User Emotion: STRESSED\nUser says: hello",
                LlamaService.buildUserContent("stressed", "hello"));
    }

    @Test
    void nullEmotionDefaultsToNeutral() {
        assertEquals("User Emotion: NEUTRAL\nUser says: hello",
                LlamaService.buildUserContent(null, "hello"));
    }

    @Test
    void extractContentReadsDelta() {
        String sse = "{\"choices\":[{\"delta\":{\"content\":\"Hello\"}}]}";

        assertEquals("Hello", LlamaService.extractContent(objectMapper, sse));
    }

    @Test
    void extractContentIgnoresRoleOnlyDelta() {
        String sse = "{\"choices\":[{\"delta\":{\"role\":\"assistant\"}}]}";

        assertNull(LlamaService.extractContent(objectMapper, sse));
    }

    @Test
    void extractContentSkipsMalformedJson() {
        assertNull(LlamaService.extractContent(objectMapper, "not-json{{{"));
        assertNull(LlamaService.extractContent(objectMapper, ""));
    }

    @Test
    void textDeltasDropContentlessChunksWithoutNullSignal() {
        // Every Groq stream opens with a role-only delta; reasoning models add
        // more contentless chunks. The pipeline must drop them, not NPE.
        Flux<String> lines = Flux.just(
                "{\"choices\":[{\"delta\":{\"role\":\"assistant\"}}]}",
                "{\"choices\":[{\"delta\":{\"content\":\"Hi\"}}]}",
                "[DONE]",
                "not-json{{{",
                "{\"choices\":[{\"delta\":{}}]}");

        List<String> deltas = LlamaService.toTextDeltas(objectMapper, lines).collectList().block();

        assertEquals(List.of("Hi"), deltas);
    }
}
