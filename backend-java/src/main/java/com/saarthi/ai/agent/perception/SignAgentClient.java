package com.saarthi.ai.agent.perception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Sign-language classification via the Python sidecar (POST /predict-sign).
 * The frontend sends only 21 hand-landmark triples per frame - never video -
 * and this client turns them into a sign label. Fully failure-proof per
 * 08_AGENT_CONNECTION.md section 7: Python down, slow, or confused degrades
 * to "no sign", NEVER to an exception - voice and text are unaffected.
 * Confidence thresholding happens here, so every caller shares one rule:
 * only labels strictly above the threshold are returned.
 */
@Slf4j
@Service
public class SignAgentClient {

    /** Canonical "no sign" result: null label, zero confidence. */
    public static final SignResult EMPTY = new SignResult(null, 0.0);

    /** Classified sign label plus its confidence (0.0-1.0). */
    public record SignResult(String sign, double confidence) {
    }

    private final WebClient pythonWebClient;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final double confidenceThreshold;
    private final Duration timeout;

    public SignAgentClient(
            @Qualifier("pythonWebClient") WebClient pythonWebClient,
            ObjectMapper objectMapper,
            @Value("${saarthi.sign.enabled:true}") boolean enabled,
            @Value("${saarthi.sign.confidence-threshold:0.80}") double confidenceThreshold,
            @Value("${saarthi.sign.timeout-ms:2000}") long timeoutMs) {
        this.pythonWebClient = pythonWebClient;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.confidenceThreshold = confidenceThreshold;
        this.timeout = Duration.ofMillis(timeoutMs);
    }

    /**
     * Classifies one hand frame. Returns EMPTY when disabled, when the frame
     * is malformed, when Python is unreachable/slow, or when the label is at
     * or below the confidence threshold. Never throws, never returns null.
     */
    public Mono<SignResult> predictSign(List<List<Double>> landmarks) {
        if (!enabled) {
            return Mono.just(EMPTY);
        }
        if (!isValidFrame(landmarks)) {
            return Mono.just(EMPTY);
        }
        long startNanos = System.nanoTime();
        return pythonWebClient.post()
                .uri("/predict-sign")
                .bodyValue(Map.of("landmarks", landmarks))
                .retrieve()
                .bodyToMono(String.class)
                .timeout(timeout)
                .map(json -> parseSignResult(objectMapper, json, confidenceThreshold))
                .doOnNext(result -> {
                    if (result.sign() != null) {
                        log.info("[SignAgent] Latency: {}ms, sign: {}",
                                (System.nanoTime() - startNanos) / 1_000_000, result.sign());
                    }
                })
                .onErrorResume(e -> {
                    log.warn("[SignAgent] unavailable, ignoring sign input");
                    return Mono.just(EMPTY);
                });
    }

    /**
     * A frame is valid only with exactly 21 landmarks of 3 finite numbers.
     * Anything else (hand half out of view, corrupt payload) is routine and
     * degrades to EMPTY without a log line.
     */
    static boolean isValidFrame(List<List<Double>> landmarks) {
        if (landmarks == null || landmarks.size() != 21) {
            return false;
        }
        for (List<Double> point : landmarks) {
            if (point == null || point.size() != 3) {
                return false;
            }
            for (Double value : point) {
                if (value == null || !Double.isFinite(value)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Parses {"sign": "HELP"|null, "confidence": 0.94}. Labels at or below
     * the threshold, blank labels, and unparsable bodies all become EMPTY.
     */
    static SignResult parseSignResult(ObjectMapper objectMapper, String json, double threshold) {
        if (json == null || json.isBlank()) {
            return EMPTY;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode sign = root.path("sign");
            JsonNode confidence = root.path("confidence");
            String label = sign.isTextual() ? sign.asText().trim().toUpperCase() : "";
            double score = confidence.isNumber() ? confidence.asDouble() : 0.0;
            if (label.isEmpty() || score <= threshold) {
                return EMPTY;
            }
            return new SignResult(label, score);
        } catch (JsonProcessingException e) {
            log.warn("[SignAgent] Skipping unparsable sign response");
            return EMPTY;
        }
    }
}
