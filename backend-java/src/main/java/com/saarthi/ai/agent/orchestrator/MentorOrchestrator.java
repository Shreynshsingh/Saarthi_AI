package com.saarthi.ai.agent.orchestrator;

import com.saarthi.ai.agent.action.VoiceAgent;
import com.saarthi.ai.agent.action.avatar.AvatarAgent;
import com.saarthi.ai.agent.action.avatar.AvatarStreamRegistry;
import com.saarthi.ai.agent.action.avatar.AvatarUnavailableException;
import com.saarthi.ai.agent.core.MemoryAgent;
import com.saarthi.ai.agent.core.MentorBrainAgent;
import com.saarthi.ai.agent.core.SafetyAgent;
import com.saarthi.ai.agent.perception.SignAgentClient;
import com.saarthi.ai.agent.perception.SpeechAgent;
import com.saarthi.ai.dto.AgentContext;
import com.saarthi.ai.dto.ChatRequest;
import com.saarthi.ai.dto.ChatResponse;
import com.saarthi.ai.dto.WsMessageType;
import com.saarthi.ai.exception.SpeechTranscriptionException;
import com.saarthi.ai.model.Conversation;
import com.saarthi.ai.model.MoodType;
import com.saarthi.ai.model.User;
import com.saarthi.ai.service.SentenceBuffer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The ONLY class that connects agents (see 08_AGENT_CONNECTION.md).
 * Exact flow: resolve input (transcribe VOICE) -&gt; blank check -&gt; Safety gate
 * -&gt; Conversation check -&gt; Memory context -&gt; Brain stream -&gt; sentence TTS
 * -&gt; persist -&gt; END. Agents never call each other.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MentorOrchestrator {

    private static final String BLANK_TEXT_ERROR =
            "I couldn't hear that clearly. Take your time, I'm here.";
    private static final String GROQ_ERROR =
            "I'm here with you. Please give me a moment and try again.";
    private static final String CONVERSATION_ERROR =
            "I couldn't find that conversation. Please start a new one.";
    private static final String SIGN_UNCLEAR_ERROR =
            "I couldn't understand that sign clearly. Please hold the sign steady, or use Text or Voice.";
    private static final double MIN_SIGN_CONFIDENCE = 0.80;

    /**
     * Sign code words are never spoken to Llama: each label maps to the human
     * sentence that is saved and mentored on, so the reply sounds natural.
     */
    private static final Map<String, String> SIGN_TO_SENTENCE = Map.of(
            "HELP", "I need help",
            "I_NEED_HELP", "I need help",
            "SAD", "I am feeling sad",
            "STRESS", "I am feeling stressed",
            "HELLO", "Hello",
            "THANK_YOU", "Thank you",
            "YES", "Yes",
            "NO", "No",
            "PLEASE", "Please help me",
            "HAPPY", "I am feeling happy");

    private final SafetyAgent safetyAgent;
    private final MemoryAgent memoryAgent;
    private final MentorBrainAgent brainAgent;
    private final SpeechAgent speechAgent;
    private final SignAgentClient signAgent;
    private final VoiceAgent voiceAgent;
    private final AvatarAgent avatarAgent;
    private final AvatarStreamRegistry avatarStreams;

    /**
     * Identity (userId) comes from the session-bound context; every input field
     * comes from the raw request. The two are never mixed: a spoofed payload
     * field can neither impersonate a user nor bypass ownership checks.
     */
    public Flux<ChatResponse> processUserMessage(AgentContext context, ChatRequest request) {
        long startNanos = System.nanoTime();
        UUID userId = context.userId();
        UUID conversationId = request.conversationId();
        String inputMode = request.inputMode() == null ? "TEXT" : request.inputMode().trim().toUpperCase();

        // 0. Resolve input: transcribe voice, trim text, classify sign.
        // Mode isolation: only the SIGN branch calls the sign client, and it
        // never requires audioBase64; VOICE/TEXT never touch sign input.
        String userText;
        String signLabel = null;
        long whisperStart = System.nanoTime();
        if ("VOICE".equals(inputMode)) {
            userText = transcribeOrEmpty(request.audioBase64(), inputMode);
        } else if ("SIGN".equals(inputMode)) {
            String label = null;
            if (request.landmarks() != null && !request.landmarks().isEmpty()) {
                // Landmarks win whenever present (the client may send both
                // fields during transition).
                SignAgentClient.SignResult result =
                        signAgent.predictSign(request.landmarks()).block();
                if (result != null && result.sign() != null
                        && result.confidence() >= MIN_SIGN_CONFIDENCE) {
                    label = result.sign();
                }
            } else if (request.signDetected() != null && !request.signDetected().isBlank()) {
                // Hint only, no landmarks to verify against: trusted as the
                // label, but still mapped to a sentence below - the raw code
                // word is never saved or spoken to Llama.
                label = request.signDetected().trim().toUpperCase();
            }
            String sentence = mapSignToSentence(label);
            if (sentence == null) {
                // Unclear sign: one calm ERROR, no safety/Llama call, and no
                // user message saved.
                logTotal(startNanos, 0, 0);
                return Flux.just(new ChatResponse(WsMessageType.ERROR, SIGN_UNCLEAR_ERROR,
                        null, null, conversationId, true, false));
            }
            signLabel = label.trim().toUpperCase();
            userText = sentence;
        } else {
            userText = request.text() == null ? "" : request.text().trim();
        }
        final long whisperMs = "VOICE".equals(inputMode)
                ? (System.nanoTime() - whisperStart) / 1_000_000 : 0;

        // 1. Blank input (or failed transcription) - nothing to mentor on.
        if (userText.isBlank()) {
            logTotal(startNanos, whisperMs, 0);
            return Flux.just(new ChatResponse(WsMessageType.ERROR, BLANK_TEXT_ERROR,
                    null, null, conversationId, true, false));
        }
        MoodType emotion = MemoryAgent.parseEmotion(request.emotion()).orElse(null);

        // 2. SAFETY GATE - bypasses Llama completely on crisis, but the user
        // message is still saved. Runs before the conversation check so a stale
        // id can never swallow a crisis reply.
        if (safetyAgent.isCrisis(userText)) {
            Conversation crisisConversation = lenientConversation(context);
            String crisisReply = safetyAgent.getHelplineResponse();
            memoryAgent.saveUserMessage(crisisConversation.getId(), userText, inputMode);
            memoryAgent.saveMentorMessage(crisisConversation.getId(), crisisReply);
            logTotal(startNanos, whisperMs, 0);
            return Flux.just(new ChatResponse(WsMessageType.CRISIS_ALERT, crisisReply,
                    null, null, crisisConversation.getId(), true, false));
        }

        // 3. Conversation (strict) + persist the user message.
        Conversation conversation;
        try {
            conversation = memoryAgent.getOrCreateConversation(userId, conversationId);
        } catch (IllegalArgumentException e) {
            log.warn("[Orchestrator] Invalid conversation id for user={}", userId);
            logTotal(startNanos, whisperMs, 0);
            return Flux.just(new ChatResponse(WsMessageType.ERROR, CONVERSATION_ERROR,
                    null, null, conversationId, true, false));
        }
        memoryAgent.saveUserMessage(conversation.getId(), userText, inputMode);

        // 4. MEMORY + BRAIN + VOICE: stream Llama tokens, speak sentence by sentence.
        String moodHistory = memoryAgent.getRecentMoods(userId);
        User user = memoryAgent.findUser(userId).orElse(null);
        String userName = user == null ? null : user.getName();
        String preferredLanguage = user == null ? null : user.getPreferredLanguage();
        StringBuilder fullReply = new StringBuilder();
        AtomicInteger chunkCount = new AtomicInteger();
        AtomicLong firstTokenNanos = new AtomicLong(-1);
        ChatResponse endMessage = new ChatResponse(WsMessageType.MENTOR_RESPONSE_END,
                null, null, null, conversation.getId(), true, false);

        Flux<String> tokens = brainAgent.streamResponse(
                        userText, request.emotion(), moodHistory, userName, preferredLanguage)
                .doOnNext(token -> firstTokenNanos.compareAndSet(-1L, System.nanoTime()));
        Flux<ChatResponse> replyStream = SentenceBuffer.toSentences(tokens)
                .switchIfEmpty(Flux.error(new IllegalStateException("Empty reply stream")))
                // Sequential per sentence: text and voice stay in sync, and a TTS
                // failure (empty) degrades only its own sentence to text-only.
                // The live avatar voices the identical bytes when it can; the
                // avatarSpeaking flag tells the frontend per sentence, so a D-ID
                // failure degrades only its own sentence to local playback.
                // The blocking provider call runs on boundedElastic, never on
                // the event loop carrying the Llama stream.
                .concatMap(sentence -> voiceAgent.textToSpeech(sentence, preferredLanguage)
                        .flatMap(audio -> {
                            String base64 = Base64.getEncoder().encodeToString(audio);
                            return Mono.fromCallable(() -> tryAvatarTalk(userId,
                                            request.avatarStreamId(), request.avatarSessionId(), base64))
                                    .subscribeOn(Schedulers.boundedElastic())
                                    .map(spoken -> new ChatResponse(WsMessageType.MENTOR_RESPONSE_CHUNK,
                                            sentence, null, base64,
                                            conversation.getId(), false, spoken));
                        })
                        .switchIfEmpty(Mono.just(new ChatResponse(WsMessageType.MENTOR_RESPONSE_CHUNK,
                                sentence, null, null, conversation.getId(), false, false))))
                .doOnNext(chunk -> {
                    chunkCount.incrementAndGet();
                    fullReply.append(chunk.textChunk()).append(' ');
                })
                // Everything downstream (sends + saves) runs on an elastic thread,
                // never blocking Groq's Netty event loop with JPA calls.
                .publishOn(Schedulers.boundedElastic())
                // 5. Persist the full reply, THEN emit END - so a client that
                // receives END knows the reply and mood are already stored.
                .concatWith(Mono.fromRunnable(() ->
                        persistReply(userId, conversation.getId(), emotion,
                                request.emotionConfidence(), fullReply, chunkCount,
                                startNanos, whisperMs, firstTokenNanos))
                        .then(Mono.just(endMessage)))
                // 6. Any failure becomes a calm ERROR message. The real cause
                // stays in server logs - never the wire, never the client.
                .onErrorResume(e -> {
                    log.warn("[Orchestrator] Brain failed: {}", e.toString(), e);
                    return Flux.just(new ChatResponse(WsMessageType.ERROR, GROQ_ERROR,
                            null, null, conversation.getId(), true, false));
                });

        if ("VOICE".equals(inputMode)) {
            ChatResponse transcription = new ChatResponse(WsMessageType.TRANSCRIPTION,
                    null, userText, null, conversation.getId(), false, false);
            return Flux.concat(Flux.just(transcription), replyStream);
        }
        if ("SIGN".equals(inputMode)) {
            ChatResponse transcription = new ChatResponse(WsMessageType.TRANSCRIPTION,
                    null, "[Sign: " + signLabel + "] " + userText, null, conversation.getId(),
                    false, false);
            return Flux.concat(Flux.just(transcription), replyStream);
        }
        return replyStream;
    }

    /**
     * Voices one sentence through the caller's live avatar. True only when the
     * request names a stream that is registered to this user with a matching
     * session id AND the provider accepted the talk. Every other outcome (no
     * stream, foreign stream, provider down, out of credits) is false, and the
     * frontend plays the sentence locally instead. Never throws, never blocks
     * or reorders chunks: the audioBase64 chunk is always still sent.
     */
    private boolean tryAvatarTalk(UUID userId, String streamId, String sessionId, String audioBase64) {
        if (streamId == null || streamId.isBlank() || sessionId == null || sessionId.isBlank()) {
            return false;
        }
        String expected = avatarStreams.verify(userId, streamId);
        if (expected == null || !expected.equals(sessionId)) {
            return false;
        }
        try {
            avatarAgent.sendTalkWithAudio(streamId, sessionId, audioBase64);
            return true;
        } catch (AvatarUnavailableException e) {
            log.warn("[Orchestrator] Avatar talk failed, local playback for this sentence");
            return false;
        }
    }

    /**
     * Maps a sign label to the human sentence saved and mentored on. Unknown
     * or null labels yield null - the caller turns that into the calm
     * unclear-sign ERROR instead of speaking a code word to Llama.
     */
    public static String mapSignToSentence(String label) {
        if (label == null || label.isBlank()) {
            return null;
        }
        return SIGN_TO_SENTENCE.get(label.trim().toUpperCase());
    }

    /**
     * Transcribes client audio. Transcription failure becomes "" and the caller
     * turns it into a calm ERROR - the session never crashes on audio problems.
     * Unexpected bugs propagate to the handler's catch-all (with a full stack).
     */
    private String transcribeOrEmpty(String audioBase64, String inputMode) {
        try {
            return speechAgent.transcribe(audioBase64, inputMode);
        } catch (SpeechTranscriptionException e) {
            log.warn("[Orchestrator] Transcription failed");
            return "";
        }
    }

    /**
     * Conversation resolution for the crisis path: prefers the requested id when
     * valid and owned, but always falls back to a fresh conversation so a crisis
     * reply is never blocked by a stale id.
     */
    private Conversation lenientConversation(AgentContext context) {
        try {
            return memoryAgent.getOrCreateConversation(context.userId(), context.conversationId());
        } catch (IllegalArgumentException e) {
            return memoryAgent.getOrCreateConversation(context.userId(), null);
        }
    }

    /**
     * Persists the full mentor reply + mood. Logs metadata only,
     * never conversation text (privacy rule).
     */
    private void persistReply(UUID userId, UUID conversationId, MoodType emotion,
                              Double emotionConfidence, StringBuilder fullReply,
                              AtomicInteger chunkCount, long startNanos,
                              long whisperMs, AtomicLong firstTokenNanos) {
        String fullResponse = fullReply.toString().trim();
        memoryAgent.saveMentorMessage(conversationId, fullResponse);
        if (emotion != null) {
            memoryAgent.saveMood(userId, conversationId, emotion, emotionConfidence);
        }
        long firstTokenMs = firstTokenNanos.get() < 0 ? 0
                : (firstTokenNanos.get() - startNanos) / 1_000_000;
        log.info("[Orchestrator] Reply complete: user={} conversation={} chunks={} chars={} latency={}ms",
                userId, conversationId, chunkCount.get(), fullResponse.length(),
                (System.nanoTime() - startNanos) / 1_000_000);
        logTotal(startNanos, whisperMs, firstTokenMs);
    }

    /**
     * Per-message latency breakdown. A 0ms LlamaFirstToken means Llama was never
     * called on this path (blank input, crisis, rejected conversation).
     */
    private void logTotal(long startNanos, long whisperMs, long firstTokenMs) {
        log.info("[Orchestrator] Total latency: {}ms | Whisper: {}ms | LlamaFirstToken: {}ms",
                (System.nanoTime() - startNanos) / 1_000_000, whisperMs, firstTokenMs);
    }
}
