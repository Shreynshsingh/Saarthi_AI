package com.saarthi.ai.agent.perception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saarthi.ai.exception.SpeechTranscriptionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Base64;

/**
 * Speech-to-text via Groq Whisper. Auto-detects English, Hindi, and Hinglish -
 * no manual language selection. Called only from the orchestrator, on a virtual
 * thread, so the blocking call below never stalls an event loop.
 * Audio bytes are never saved to disk or the database.
 */
@Slf4j
@Service
public class SpeechAgent {

    private final WebClient groqWebClient;
    private final ObjectMapper objectMapper;
    private final String whisperModel;
    private final Duration timeout;

    public SpeechAgent(
            @Qualifier("groqWebClient") WebClient groqWebClient,
            ObjectMapper objectMapper,
            @Value("${groq.whisper.model:whisper-large-v3}") String whisperModel,
            @Value("${saarthi.python-service.stt-timeout-ms:10000}") long sttTimeoutMs) {
        this.groqWebClient = groqWebClient;
        this.objectMapper = objectMapper;
        this.whisperModel = whisperModel;
        this.timeout = Duration.ofMillis(sttTimeoutMs);
    }

    /**
     * Transcribes Base64 client audio. Returns "" for non-voice input, missing
     * audio, bad Base64, or empty results. Throws SpeechTranscriptionException
     * (converted to a calm ERROR downstream) on timeout or API failure.
     * Never returns null, never exposes provider errors.
     */
    public String transcribe(String audioBase64, String inputMode) {
        if (!"VOICE".equals(inputMode == null ? "" : inputMode.trim().toUpperCase())) {
            return "";
        }
        if (audioBase64 == null || audioBase64.isBlank()) {
            return "";
        }
        byte[] audio;
        try {
            audio = Base64.getMimeDecoder().decode(audioBase64.trim());
        } catch (IllegalArgumentException e) {
            log.error("[SpeechAgent] Invalid audio payload");
            return "";
        }
        if (audio.length == 0) {
            return "";
        }
        long startNanos = System.nanoTime();
        ByteArrayResource file = new ByteArrayResource(audio) {
            @Override
            public String getFilename() {
                return "audio.webm";
            }
        };
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.parseMediaType("audio/webm"));
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new HttpEntity<>(file, fileHeaders));
        form.add("model", whisperModel);
        form.add("response_format", "json");
        try {
            String json = groqWebClient.post()
                    .uri("/audio/transcriptions")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .bodyValue(form)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), response ->
                            response.bodyToMono(String.class).map(errorBody -> {
                                log.error("[SpeechAgent] Whisper API error: status={}",
                                        response.statusCode());
                                return new SpeechTranscriptionException("Transcription failed");
                            }))
                    .bodyToMono(String.class)
                    .timeout(timeout)
                    .block();
            String text = parseTranscription(objectMapper, json);
            log.info("[SpeechAgent] Whisper latency: {}ms, chars: {}",
                    (System.nanoTime() - startNanos) / 1_000_000, text.length());
            return text;
        } catch (RuntimeException e) {
            log.warn("[SpeechAgent] Transcription failed: {}", e.getClass().getSimpleName());
            throw new SpeechTranscriptionException("Transcription failed", e);
        }
    }

    static String parseTranscription(ObjectMapper objectMapper, String json) {
        if (json == null || json.isBlank()) {
            return "";
        }
        try {
            JsonNode text = objectMapper.readTree(json).path("text");
            return text.isTextual() ? text.asText().trim() : "";
        } catch (JsonProcessingException e) {
            log.warn("[SpeechAgent] Skipping unparsable transcription response");
            return "";
        }
    }
}
