package com.saarthi.ai.service;

import com.saarthi.ai.agent.core.MemoryAgent;
import com.saarthi.ai.model.MoodLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Dashboard insight card: a Llama-written, 2-sentence reading of the user's
 * recent mood pattern. Warm and second-person, never a medical claim,
 * diagnosis, or treatment suggestion. Any failure (no history, Groq down,
 * empty reply) degrades to a calm static line - the dashboard never breaks
 * on insights.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MoodInsightService {

    private static final String SYSTEM_PROMPT =
            "You are Saarthi, a warm mentor. Summarize the user's recent mood pattern "
            + "in exactly 2 short sentences, second person. Be encouraging, never clinical. "
            + "Never diagnose, never suggest medication or therapy plans.";

    private static final String NO_HISTORY_INSIGHT =
            "Your journey is just beginning. Check in or start a session, and I'll "
            + "start noticing patterns with you here.";

    private static final String FALLBACK_INSIGHT =
            "Every day you show up here matters. I'm glad you're checking in with yourself.";

    private final MemoryAgent memoryAgent;
    private final LlamaService llamaService;

    public String generateInsight(UUID userId) {
        List<MoodLog> history = memoryAgent.getMoodHistory(userId, 7);
        if (history.isEmpty()) {
            return NO_HISTORY_INSIGHT;
        }
        String pattern = history.stream()
                .limit(20)
                .map(entry -> entry.getMood().name()
                        + (entry.getConfidence() == null ? ""
                                : " (" + Math.round(entry.getConfidence() * 100) + "%)"))
                .collect(Collectors.joining(", "));
        try {
            List<String> parts = llamaService
                    .streamResponse(SYSTEM_PROMPT, "NEUTRAL",
                            "Mood entries, most recent first: " + pattern)
                    .collectList()
                    .block();
            String insight = parts == null ? "" : String.join("", parts).trim();
            if (insight.isEmpty()) {
                return FALLBACK_INSIGHT;
            }
            return insight;
        } catch (RuntimeException e) {
            log.warn("[MoodInsight] Llama failed, static insight: {}",
                    e.getClass().getSimpleName());
            return FALLBACK_INSIGHT;
        }
    }
}
