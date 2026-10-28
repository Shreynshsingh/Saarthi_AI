package com.saarthi.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Groq API client (OpenAI-compatible). Builds the chat-completion request,
 * streams the SSE response, and emits text deltas only. Never buffers a full
 * reply, never throws on a bad chunk - logs it and skips it instead.
 */
@Slf4j
@Service
public class LlamaService {

    private static final double TEMPERATURE = 0.7;
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final WebClient groqWebClient;
    private final ObjectMapper objectMapper;
    private final String model;

    public LlamaService(
            @Qualifier("groqWebClient") WebClient groqWebClient,
            ObjectMapper objectMapper,
            @Value("${groq.api.model:openai/gpt-oss-120b}") String model) {
        this.groqWebClient = groqWebClient;
        this.objectMapper = objectMapper;
        this.model = model;
    }

    /**
     * Streams Llama's reply as raw text deltas. Emotion travels in the user
     * content line; the filled system prompt already carries placeholders.
     */
    public Flux<String> streamResponse(String filledSystemPrompt, String emotion, String userText) {
        Map<String, Object> body = Map.of(
                "model", model,
                "stream", true,
                "temperature", TEMPERATURE,
                "messages", List.of(
                        Map.of("role", "system", "content", filledSystemPrompt),
                        Map.of("role", "user", "content", buildUserContent(emotion, userText))
                )
        );
        long startNanos = System.nanoTime();
        AtomicBoolean firstTokenLogged = new AtomicBoolean(false);
        return groqWebClient.post()
                .uri("/chat/completions")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(body)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), response ->
                        response.bodyToMono(String.class).map(errorBody -> {
                            String snippet = errorBody.length() > 200 ? errorBody.substring(0, 200) : errorBody;
                            log.error("[LlamaService] Groq API error: status={} body={}",
                                    response.statusCode(), snippet);
                            return new IllegalStateException("Groq API error");
                        }))
                .bodyToFlux(String.class)
                .transform(lines -> toTextDeltas(objectMapper, lines))
                // Fails if Groq stalls; the orchestrator converts that into a
                // caring fallback reply, never a hang.
                .timeout(TIMEOUT)
                .doOnNext(chunk -> {
                    if (firstTokenLogged.compareAndSet(false, true)) {
                        log.info("[MentorBrainAgent] First token latency: {}ms",
                                (System.nanoTime() - startNanos) / 1_000_000);
                    }
                });
    }

    /**
     * Turns raw SSE data lines into text deltas. Role-only, reasoning-only,
     * and malformed chunks are dropped — never emitted as null, because
     * Reactor forbids null signals and would fail the whole stream.
     */
    static Flux<String> toTextDeltas(ObjectMapper objectMapper, Flux<String> sseLines) {
        return sseLines
                .filter(data -> data != null && !data.isBlank() && !"[DONE]".equals(data.trim()))
                .mapNotNull(data -> extractContent(objectMapper, data))
                .filter(chunk -> !chunk.isEmpty());
    }

    static String buildUserContent(String emotion, String userText) {
        String mood = emotion == null || emotion.isBlank() ? "NEUTRAL" : emotion.trim().toUpperCase();
        return "User Emotion: " + mood + "\nUser says: " + userText;
    }

    static String extractContent(ObjectMapper objectMapper, String sseData) {
        try {
            JsonNode content = objectMapper.readTree(sseData)
                    .path("choices").path(0).path("delta").path("content");
            return content.isTextual() ? content.asText() : null;
        } catch (JsonProcessingException e) {
            log.warn("[LlamaService] Skipping unparsable SSE chunk");
            return null;
        }
    }
}
