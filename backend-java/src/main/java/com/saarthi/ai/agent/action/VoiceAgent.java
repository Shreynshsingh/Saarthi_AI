package com.saarthi.ai.agent.action;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Text-to-speech via the Python sidecar (POST /tts, binary mp3 back).
 * Fully reactive and failure-proof: ANY error becomes Mono.empty(), and the
 * orchestrator still sends the text chunk without audio. TTS can never break
 * a conversation.
 */
@Slf4j
@Service
public class VoiceAgent {

    private static final Set<String> HINGLISH_MARKERS = Set.of(
            "hoon", "hun", "hai", "hain", "nahi", "nahin", "nahii", "kya", "kaise",
            "bahut", "bohot", "zyada", "jyada", "achha", "acha", "theek", "thik",
            "tum", "tumhe", "tumne", "tumko", "tumhara", "tumhari", "mujhe",
            "mera", "meri", "mere", "liye", "saath", "mein", "humne", "humara",
            "hamara", "aap", "aapko", "aapka", "yeh", "wohi", "yahi", "wahi",
            "raha", "rahi", "rahe", "gaya", "gayi", "gayee", "karta", "karti",
            "karte", "wala", "wali", "wale", "chahiye", "sakta", "sakti",
            "sakte", "pata", "arre", "yaar", "bhai", "dost", "aur", "bhi",
            "toh", "lekin", "agar", "magar", "kyunki", "kyun", "kaun", "kahan",
            "kidhar", "kabhi", "hamesha", "abhi", "phir", "fir", "sab", "sabse",
            "yahan", "wahan", "upar", "andar", "bahar", "uska", "uski", "jiska");

    private final WebClient pythonWebClient;
    private final Duration timeout;

    public VoiceAgent(
            @Qualifier("pythonWebClient") WebClient pythonWebClient,
            @Value("${saarthi.python-service.tts-timeout-ms:5000}") long ttsTimeoutMs) {
        this.pythonWebClient = pythonWebClient;
        this.timeout = Duration.ofMillis(ttsTimeoutMs);
    }

    /**
     * Synthesizes one sentence, returning Base64 mp3. Blank input yields empty.
     * On ANY error logs WARN and returns Mono.empty() - the orchestrator then
     * sends the text chunk without audio.
     */
    public Mono<String> textToSpeechBase64(String sentence, String languageHint) {
        return textToSpeech(sentence, languageHint)
                .map(audio -> Base64.getEncoder().encodeToString(audio));
    }

    /**
     * Same synthesis returning raw MP3 bytes, so the avatar agent can voice
     * the identical audio through D-ID without a wasteful re-encode.
     */
    public Mono<byte[]> textToSpeech(String sentence, String languageHint) {
        if (sentence == null || sentence.isBlank()) {
            return Mono.empty();
        }
        String voice = selectVoice(sentence, languageHint);
        long startNanos = System.nanoTime();
        return pythonWebClient.post()
                .uri("/tts")
                .accept(MediaType.parseMediaType("audio/mpeg"))
                .bodyValue(Map.of("text", sentence, "voice", voice))
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), response ->
                        response.bodyToMono(String.class).map(errorBody ->
                                new IllegalStateException("TTS service error")))
                .bodyToMono(byte[].class)
                .timeout(timeout)
                .filter(audio -> audio.length > 0)
                .doOnNext(audio -> log.info("[VoiceAgent] TTS latency: {}ms, sentence chars: {}",
                        (System.nanoTime() - startNanos) / 1_000_000, sentence.length()))
                .onErrorResume(e -> {
                    log.warn("[VoiceAgent] TTS failed, text-only chunk: {}",
                            e.getClass().getSimpleName());
                    return Mono.empty();
                });
    }

    /**
     * One simple rule: explicit hi-IN hint, Devanagari script, or a Hinglish
     * marker word picks the Hindi voice; everything else uses Indian English.
     */
    static String selectVoice(String sentence, String languageHint) {
        if (languageHint != null && languageHint.trim().toLowerCase(Locale.ROOT).startsWith("hi")) {
            return "hi-IN-SwaraNeural";
        }
        if (sentence == null) {
            return "en-IN-NeerjaNeural";
        }
        for (int i = 0; i < sentence.length(); i++) {
            char c = sentence.charAt(i);
            if (c >= '\u0900' && c <= '\u097F') {
                return "hi-IN-SwaraNeural";
            }
        }
        for (String token : sentence.toLowerCase(Locale.ROOT).split("[^a-z]+")) {
            if (HINGLISH_MARKERS.contains(token)) {
                return "hi-IN-SwaraNeural";
            }
        }
        return "en-IN-NeerjaNeural";
    }
}
