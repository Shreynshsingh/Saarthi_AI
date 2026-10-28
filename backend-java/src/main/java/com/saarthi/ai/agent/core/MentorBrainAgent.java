package com.saarthi.ai.agent.core;

import com.saarthi.ai.service.LlamaService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Thin wrapper over {@link LlamaService}: owns the mentor persona (system prompt
 * template + placeholders) and mood-history context, delegates all Groq HTTP/SSE
 * streaming to LlamaService.
 */
@Slf4j
@Service
public class MentorBrainAgent {

    private final LlamaService llamaService;
    private final Resource promptResource;

    private String systemPrompt;

    public MentorBrainAgent(
            LlamaService llamaService,
            @Value("classpath:prompts/saarthi-system-prompt.txt") Resource promptResource) {
        this.llamaService = llamaService;
        this.promptResource = promptResource;
    }

    @PostConstruct
    void loadSystemPrompt() {
        try (InputStream in = promptResource.getInputStream()) {
            systemPrompt = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
            log.info("[Brain] System prompt loaded ({} chars)", systemPrompt.length());
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load Saarthi system prompt", e);
        }
    }

    /**
     * Resolves placeholders, appends mood context when present, and streams
     * the reply via LlamaService.
     */
    public Flux<String> streamResponse(String userText, String emotion, String moodHistory,
                                       String userName, String preferredLanguage) {
        String filled = resolvePlaceholders(systemPrompt, userName, emotion, preferredLanguage);
        if (moodHistory != null && !moodHistory.isBlank()) {
            filled = filled + "\n[Recent moods: " + moodHistory.trim() + "]";
        }
        return llamaService.streamResponse(filled, emotion, userText);
    }

    /**
     * Replaces the session placeholders in the cached template. Null/blank inputs
     * fall back to safe defaults so a missing value never breaks the prompt.
     */
    static String resolvePlaceholders(String template, String userName, String emotion,
                                      String preferredLanguage) {
        String name = userName == null || userName.isBlank() ? "friend" : userName.trim();
        String mood = emotion == null || emotion.isBlank() ? "NEUTRAL" : emotion.trim().toUpperCase();
        String language = preferredLanguage == null || preferredLanguage.isBlank()
                ? "English" : preferredLanguage.trim();
        return template
                .replace("{{user_name}}", name)
                .replace("{{emotion}}", mood)
                .replace("{{preferred_language}}", language);
    }
}
