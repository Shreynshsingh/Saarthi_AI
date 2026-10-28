package com.saarthi.ai.service;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sentence buffering: tokens in, complete sentences out. Pure unit tests.
 */
class SentenceBufferTest {

    @Test
    void splitsOnBoundaries() {
        List<String> out = SentenceBuffer.toSentences(
                        Flux.just("Hel", "lo. How", " are", " you? F", "ine"))
                .collectList().block();

        assertEquals(List.of("Hello.", "How are you?", "Fine"), out);
    }

    @Test
    void handlesDandaAndFlush() {
        List<String> out = SentenceBuffer.toSentences(
                        Flux.just("नमस्ते। ", "आप कैसे", " हैं"))
                .collectList().block();

        assertEquals(List.of("नमस्ते।", "आप कैसे हैं"), out);
    }

    @Test
    void skipsBlanks() {
        List<String> out = SentenceBuffer.toSentences(Flux.just("  ", "", "Hi."))
                .collectList().block();

        assertEquals(List.of("Hi."), out);
    }

    @Test
    void forceSplitsRunawayBuffer() {
        String longText = "word ".repeat(100); // 500 chars, no boundary
        List<String> out = SentenceBuffer.toSentences(Flux.just(longText))
                .collectList().block();

        assertTrue(out.size() >= 2);
        assertEquals(longText.trim(), String.join(" ", out));
    }
}
