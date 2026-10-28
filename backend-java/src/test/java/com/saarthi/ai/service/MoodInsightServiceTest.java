package com.saarthi.ai.service;

import com.saarthi.ai.agent.core.MemoryAgent;
import com.saarthi.ai.model.MoodLog;
import com.saarthi.ai.model.MoodType;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Insight fallbacks first: empty history and Llama failure must both yield
 * calm static lines, never throw, never call Groq pointlessly.
 */
class MoodInsightServiceTest {

    @Test
    void emptyHistorySkipsLlama() {
        MemoryAgent memory = new StubMemory(List.of());
        StubLlama llama = new StubLlama(Flux.just("should not be used"));

        String insight = new MoodInsightService(memory, llama).generateInsight(UUID.randomUUID());

        assertTrue(!llama.called, "No history means no Llama call");
        assertTrue(insight.contains("just beginning"), insight);
    }

    @Test
    void historyJoinsStreamedDeltas() {
        MemoryAgent memory = new StubMemory(List.of(
                MoodLog.builder().mood(MoodType.STRESSED).confidence(0.87f).build(),
                MoodLog.builder().mood(MoodType.HAPPY).confidence(null).build()));
        StubLlama llama = new StubLlama(Flux.just("You ", "had ", "a week."));

        String insight = new MoodInsightService(memory, llama).generateInsight(UUID.randomUUID());

        assertTrue(llama.called);
        assertEquals("You had a week.", insight);
        assertTrue(llama.capturedUserText.contains("STRESSED (87%)"), llama.capturedUserText);
        assertTrue(llama.capturedUserText.contains("HAPPY"), llama.capturedUserText);
    }

    @Test
    void llamaFailureFallsBackCalmly() {
        MemoryAgent memory = new StubMemory(
                List.of(MoodLog.builder().mood(MoodType.SAD).build()));
        StubLlama llama = new StubLlama(Flux.error(new RuntimeException("groq-500")));

        String insight = new MoodInsightService(memory, llama).generateInsight(UUID.randomUUID());

        assertTrue(insight.contains("show up here matters"), insight);
        assertTrue(!insight.contains("groq-500"), "Provider errors never leak to users");
    }

    @Test
    void blankReplyFallsBackCalmly() {
        MemoryAgent memory = new StubMemory(
                List.of(MoodLog.builder().mood(MoodType.SAD).build()));
        StubLlama llama = new StubLlama(Flux.just("   "));

        String insight = new MoodInsightService(memory, llama).generateInsight(UUID.randomUUID());

        assertTrue(insight.contains("show up here matters"), insight);
    }

    static class StubMemory extends MemoryAgent {
        private final List<MoodLog> history;

        StubMemory(List<MoodLog> history) {
            super(null, null, null, null, null);
            this.history = history;
        }

        @Override
        public List<MoodLog> getMoodHistory(UUID userId, int days) {
            return history;
        }
    }

    static class StubLlama extends LlamaService {
        boolean called;
        String capturedUserText;
        private final Flux<String> script;

        StubLlama(Flux<String> script) {
            super(null, null, "test-model");
            this.script = script;
        }

        @Override
        public Flux<String> streamResponse(String filledSystemPrompt, String emotion, String userText) {
            called = true;
            capturedUserText = userText;
            return script;
        }
    }
}
