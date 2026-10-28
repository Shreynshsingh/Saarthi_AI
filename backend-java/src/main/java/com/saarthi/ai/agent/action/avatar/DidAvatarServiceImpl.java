package com.saarthi.ai.agent.action.avatar;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saarthi.ai.service.avatar.AvatarStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * D-ID Talks Streams implementation over plain WebClient (no SDK). All D-ID
 * request/response JSON lives in the DTO records below — no other class may
 * depend on provider structure. Every call has a 10s timeout; every failure
 * logs and throws {@link AvatarUnavailableException} so callers fall back.
 * The API key is read as-is and never logged.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "saarthi.avatar.provider", havingValue = "did", matchIfMissing = true)
public class DidAvatarServiceImpl implements AvatarAgent {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String FALLBACK_MESSAGE = "Live video is unavailable right now — photo mode it is.";

    private final WebClient didWebClient;
    private final AvatarStorage storage;
    private final ObjectMapper objectMapper;
    private final boolean keyPresent;

    public DidAvatarServiceImpl(
            @Qualifier("didWebClient") WebClient didWebClient,
            @Value("${did.api.key:}") String apiKey,
            AvatarStorage storage,
            ObjectMapper objectMapper) {
        this.didWebClient = didWebClient;
        this.storage = storage;
        this.objectMapper = objectMapper;
        this.keyPresent = apiKey != null && !apiKey.isBlank();
        if (!keyPresent) {
            // Startup kill-switch trip: behave as static, auth and chat unaffected.
            log.error("[AvatarAgent] AVATAR_PROVIDER=did but DID_API_KEY is blank - "
                    + "live avatar disabled, running static. Auth and chat are unaffected.");
        }
    }

    /**
     * The Streams API binds its source image per stream (source_url) — there is
     * no separate presenter resource — so the validated image URL itself is the
     * provider identifier stored on the profile.
     */
    @Override
    public String createPresenter(String imageUrl) {
        requireKey();
        if (imageUrl == null || imageUrl.isBlank()
                || !(imageUrl.startsWith("http://") || imageUrl.startsWith("https://"))) {
            throw new AvatarUnavailableException(FALLBACK_MESSAGE);
        }
        return imageUrl;
    }

    @Override
    public AvatarStreamSession createStream(String presenterIdOrImageUrl) {
        requireKey();
        try {
            StreamCreated created = didWebClient.post()
                    .uri("/talks/streams")
                    .bodyValue(Map.of("source_url", presenterIdOrImageUrl))
                    .retrieve()
                    .bodyToMono(StreamCreated.class)
                    .timeout(TIMEOUT)
                    .block();
            if (created == null || created.id() == null || created.sessionId() == null
                    || created.offer() == null || created.offer().sdp() == null) {
                throw new AvatarUnavailableException(FALLBACK_MESSAGE);
            }
            String iceJson = objectMapper.writeValueAsString(
                    created.iceServers() == null ? List.of() : created.iceServers());
            log.info("[AvatarAgent] D-ID stream created: {}", created.id());
            return new AvatarStreamSession(created.id(), created.sessionId(), created.offer().sdp(), iceJson);
        } catch (AvatarUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw failed(e);
        }
    }

    @Override
    public void submitSdpAnswer(String streamId, String sessionId, String sdpAnswer) {
        requireKey();
        try {
            // D-ID Talks Streams contract: the answer posts to the /sdp sub-path
            // as an {answer:{type,sdp}, session_id} envelope.
            didWebClient.post()
                    .uri("/talks/streams/{id}/sdp", streamId)
                    .bodyValue(new SdpAnswer(new Sdp("answer", sdpAnswer), sessionId))
                    .retrieve()
                    .bodyToMono(Void.class)
                    .timeout(TIMEOUT)
                    .block();
        } catch (Exception e) {
            throw failed(e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void submitIceCandidate(String streamId, String sessionId, Object iceCandidate) {
        requireKey();
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            if (iceCandidate instanceof Map<?, ?> fields) {
                fields.forEach((key, value) -> payload.put(String.valueOf(key), value));
            }
            payload.put("session_id", sessionId);
            didWebClient.post()
                    .uri("/talks/streams/{id}/ice", streamId)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .timeout(TIMEOUT)
                    .block();
        } catch (Exception e) {
            throw failed(e);
        }
    }

    @Override
    public void sendTalkWithAudio(String streamId, String sessionId, String audioBase64) {
        requireKey();
        byte[] mp3;
        try {
            mp3 = Base64.getDecoder().decode(audioBase64 == null ? "" : audioBase64.trim());
        } catch (IllegalArgumentException e) {
            throw new AvatarUnavailableException(FALLBACK_MESSAGE);
        }
        if (mp3.length == 0) {
            throw new AvatarUnavailableException(FALLBACK_MESSAGE);
        }
        String audioUrl;
        try {
            audioUrl = storage.saveTalkAudio(mp3);
        } catch (IllegalArgumentException e) {
            throw new AvatarUnavailableException(FALLBACK_MESSAGE);
        }
        try {
            didWebClient.post()
                    .uri("/talks/streams/{id}", streamId)
                    .bodyValue(new TalkRequest(new AudioScript(audioUrl), Map.of("stitch", true), sessionId))
                    .retrieve()
                    .bodyToMono(Void.class)
                    .timeout(TIMEOUT)
                    .block();
        } catch (Exception e) {
            throw failed(e);
        }
    }

    @Override
    public void closeStream(String streamId, String sessionId) {
        if (!keyPresent) {
            return;
        }
        try {
            didWebClient.method(org.springframework.http.HttpMethod.DELETE)
                    .uri("/talks/streams/{id}", streamId)
                    .bodyValue(Map.of("session_id", sessionId))
                    .retrieve()
                    .bodyToMono(Void.class)
                    .timeout(TIMEOUT)
                    .block();
        } catch (Exception e) {
            // Teardown is best-effort by design: never fail a session close.
            log.warn("[AvatarAgent] D-ID close failed: {}", e.getClass().getSimpleName());
        }
    }

    private void requireKey() {
        if (!keyPresent) {
            throw new AvatarUnavailableException(FALLBACK_MESSAGE);
        }
    }

    private AvatarUnavailableException failed(Exception e) {
        if (e instanceof WebClientResponseException responseException) {
            log.warn("[AvatarAgent] D-ID failed ({}), fallback to static",
                    responseException.getStatusCode());
        } else {
            log.warn("[AvatarAgent] D-ID failed ({}), fallback to static",
                    e.getClass().getSimpleName());
        }
        return new AvatarUnavailableException(FALLBACK_MESSAGE);
    }

    // ---- D-ID wire DTOs (provider field names live ONLY here) ----

    @JsonIgnoreProperties(ignoreUnknown = true)
    record StreamCreated(
            String id,
            @JsonProperty("session_id") String sessionId,
            Sdp offer,
            @JsonProperty("ice_servers") List<Map<String, Object>> iceServers) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Sdp(String type, String sdp) {
    }

    record SdpAnswer(Sdp answer, @JsonProperty("session_id") String sessionId) {
    }

    record IceCandidate(String candidate, String sdpMid, Integer sdpMLineIndex,
                        @JsonProperty("session_id") String sessionId) {
    }

    record TalkRequest(AudioScript script, Map<String, Object> config,
                       @JsonProperty("session_id") String sessionId) {
    }

    record AudioScript(@JsonProperty("audio_url") String audioUrl) {
        @JsonProperty("type")
        public String type() {
            return "audio";
        }
    }
}
