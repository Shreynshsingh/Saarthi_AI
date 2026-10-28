package com.saarthi.ai.agent.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saarthi.ai.agent.action.VoiceAgent;
import com.saarthi.ai.agent.action.avatar.AvatarStreamRegistry;
import com.saarthi.ai.agent.action.avatar.StaticAvatarServiceImpl;
import com.saarthi.ai.agent.orchestrator.MentorOrchestrator;
import com.saarthi.ai.agent.perception.SignAgentClient;
import com.saarthi.ai.agent.perception.SpeechAgent;
import com.saarthi.ai.config.AuthHandshakeInterceptor;
import com.saarthi.ai.dto.WsMessageType;
import com.saarthi.ai.exception.SpeechTranscriptionException;
import com.saarthi.ai.handler.MentorWebSocketHandler;
import com.saarthi.ai.model.Conversation;
import com.saarthi.ai.model.InputMode;
import com.saarthi.ai.model.Message;
import com.saarthi.ai.model.MoodLog;
import com.saarthi.ai.model.User;
import com.saarthi.ai.repository.ConversationRepository;
import com.saarthi.ai.repository.MessageRepository;
import com.saarthi.ai.repository.MoodLogRepository;
import com.saarthi.ai.repository.UserRepository;
import com.saarthi.ai.service.LlamaService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Full vertical slice without TCP, Groq, Python, or a database: a fake
 * WebSocketSession drives the real handler -&gt; orchestrator -&gt; agents chain.
 * Repositories are map/list-backed fakes; only the Llama HTTP call, Whisper,
 * and TTS are stubbed.
 */
class MentorFlowTest {

    private static final UUID SESSION_USER = UUID.randomUUID();
    private static final UUID SPOOFED_USER = UUID.randomUUID();
    private static final UUID OTHER_USER = UUID.randomUUID();
    private static final byte[] FAKE_AUDIO = "FAKEAUDIO".getBytes();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<UUID, Conversation> conversations = new HashMap<>();
    private final List<Message> messages = new CopyOnWriteArrayList<>();
    private final List<MoodLog> moods = new CopyOnWriteArrayList<>();
    private final List<String> frames = new CopyOnWriteArrayList<>();

    private StubLlama llama;
    private StubSpeech speech;
    private StubVoice voice;
    private StubSign sign;
    private OpenHandler handler;
    private WebSocketSession session;

    @org.junit.jupiter.api.BeforeEach
    void setUp() throws Exception {
        Path prompt = Files.createTempFile("flow-prompt", ".txt");
        Files.writeString(prompt, "Mentor {{user_name}} | {{emotion}} | {{preferred_language}}");
        User sessionUser = User.builder().id(SESSION_USER).name("Aarav")
                .email("a@t.c").passwordHash("x").preferredLanguage("Hinglish").build();

        ConversationRepository convRepo = fake(ConversationRepository.class, (m, a) -> {
            if (m.getName().equals("findById")) {
                return Optional.ofNullable(conversations.get((UUID) a[0]));
            }
            if (m.getName().equals("save")) {
                Conversation c = (Conversation) a[0];
                if (c.getId() == null) {
                    c.setId(UUID.randomUUID());
                }
                conversations.put(c.getId(), c);
                return c;
            }
            throw new UnsupportedOperationException(m.getName());
        });
        MessageRepository msgRepo = fake(MessageRepository.class, (m, a) -> {
            if (m.getName().equals("save")) {
                messages.add((Message) a[0]);
                return a[0];
            }
            throw new UnsupportedOperationException(m.getName());
        });
        MoodLogRepository moodRepo = fake(MoodLogRepository.class, (m, a) -> {
            if (m.getName().equals("save")) {
                moods.add((MoodLog) a[0]);
                return a[0];
            }
            if (m.getName().equals("findByUserIdOrderByLoggedAtDesc")) {
                return List.of();
            }
            throw new UnsupportedOperationException(m.getName());
        });
        UserRepository userRepo = fake(UserRepository.class, (m, a) -> {
            if (m.getName().equals("findById")) {
                return Optional.of(sessionUser);
            }
            throw new UnsupportedOperationException(m.getName());
        });

        llama = new StubLlama();
        speech = new StubSpeech();
        voice = new StubVoice();
        sign = new StubSign();
        MemoryAgent memory = new MemoryAgent(convRepo, msgRepo, moodRepo, userRepo,
                new StubMcp());
        MentorBrainAgent brain = new MentorBrainAgent(llama, new FileSystemResource(prompt.toFile()));
        brain.loadSystemPrompt();
        MentorOrchestrator orchestrator =
                new MentorOrchestrator(new SafetyAgent(), memory, brain, speech, sign, voice,
                        new StaticAvatarServiceImpl(), new AvatarStreamRegistry());
        handler = new OpenHandler(orchestrator, objectMapper);

        Map<String, Object> attributes = new HashMap<>();
        attributes.put(AuthHandshakeInterceptor.ATTR_USER_ID, SESSION_USER);
        session = session(attributes);
        handler.afterConnectionEstablished(session);
    }

    @org.junit.jupiter.api.Test
    void textFlowStreamsSentenceWithAudioUsingSessionIdentity() throws Exception {
        // Spoofed userId in the payload must be ignored - identity comes from the session.
        String payload = "{\"type\":\"USER_MESSAGE\",\"conversationId\":null,\"inputMode\":\"TEXT\","
                + "\"text\":\"hello\",\"emotion\":\"STRESSED\",\"emotionConfidence\":0.87,"
                + "\"userId\":\"" + SPOOFED_USER + "\"}";
        handler.drive(session, new TextMessage(payload));

        awaitFrames(2);
        assertEquals(2, frames.size(), frames.toString());
        JsonNode chunk = objectMapper.readTree(frames.get(0));
        JsonNode end = objectMapper.readTree(frames.get(1));
        assertEquals(WsMessageType.MENTOR_RESPONSE_CHUNK, chunk.get("type").asText());
        assertEquals("Hello", chunk.get("textChunk").asText());
        assertEquals(Base64.getEncoder().encodeToString(FAKE_AUDIO),
                chunk.get("audioBase64").asText());
        assertEquals(false, chunk.get("avatarSpeaking").asBoolean());
        assertEquals(WsMessageType.MENTOR_RESPONSE_END, end.get("type").asText());
        assertTrue(end.get("isFinal").asBoolean());
        assertEquals(chunk.get("conversationId").asText(), end.get("conversationId").asText());

        // Session identity won: conversation + prompt used SESSION_USER, not the spoof.
        assertEquals(1, conversations.size());
        Conversation created = conversations.values().iterator().next();
        assertEquals(SESSION_USER, created.getUserId());
        assertTrue(llama.capturedPrompt.contains("Aarav"), llama.capturedPrompt);
        assertTrue(llama.capturedUserText.equals("hello"), llama.capturedUserText);

        // Buffered full reply persisted.
        assertTrue(messages.stream().anyMatch(m -> m.getRole().equals("MENTOR")
                && m.getContent().equals("Hello")));
        assertEquals(1, moods.size());
        assertEquals("STRESSED", moods.get(0).getMood().name());
    }

    @org.junit.jupiter.api.Test
    void voiceFlowTranscribesThenStreams() throws Exception {
        speech.result = "transcribed hello";
        String payload = "{\"type\":\"USER_MESSAGE\",\"conversationId\":null,\"inputMode\":\"VOICE\","
                + "\"text\":\"\",\"audioBase64\":\"aGVsbG8=\"}";
        handler.drive(session, new TextMessage(payload));

        awaitFrames(3);
        assertEquals(3, frames.size(), frames.toString());
        JsonNode transcription = objectMapper.readTree(frames.get(0));
        JsonNode chunk = objectMapper.readTree(frames.get(1));
        JsonNode end = objectMapper.readTree(frames.get(2));
        assertEquals(WsMessageType.TRANSCRIPTION, transcription.get("type").asText());
        assertEquals("transcribed hello", transcription.get("transcribedText").asText());
        assertEquals(WsMessageType.MENTOR_RESPONSE_CHUNK, chunk.get("type").asText());
        assertEquals(WsMessageType.MENTOR_RESPONSE_END, end.get("type").asText());
        assertTrue(messages.stream().anyMatch(m -> m.getRole().equals("USER")
                && m.getInputMode() == InputMode.VOICE));
        assertTrue(llama.capturedUserText.equals("transcribed hello"), llama.capturedUserText);
    }

    @org.junit.jupiter.api.Test
    void whisperFailureYieldsCalmError() throws Exception {
        speech.fail = true;
        String payload = "{\"type\":\"USER_MESSAGE\",\"inputMode\":\"VOICE\","
                + "\"audioBase64\":\"aGVsbG8=\"}";
        handler.drive(session, new TextMessage(payload));

        awaitFrames(1);
        assertEquals(1, frames.size(), frames.toString());
        JsonNode error = objectMapper.readTree(frames.get(0));
        assertEquals(WsMessageType.ERROR, error.get("type").asText());
        assertTrue(error.get("textChunk").asText().contains("couldn't hear that clearly"));
        assertTrue(!llama.called, "Llama must not be called when transcription fails");
    }

    @org.junit.jupiter.api.Test
    void ttsFailureStillDeliversFullText() throws Exception {
        voice.fail = true;
        String payload = "{\"type\":\"USER_MESSAGE\",\"inputMode\":\"TEXT\",\"text\":\"hello\"}";
        handler.drive(session, new TextMessage(payload));

        awaitFrames(2);
        assertEquals(2, frames.size(), frames.toString());
        JsonNode chunk = objectMapper.readTree(frames.get(0));
        assertEquals(WsMessageType.MENTOR_RESPONSE_CHUNK, chunk.get("type").asText());
        assertEquals("Hello", chunk.get("textChunk").asText());
        assertTrue(chunk.get("audioBase64").isNull());
        assertTrue(messages.stream().anyMatch(m -> m.getRole().equals("MENTOR")
                && m.getContent().equals("Hello")));
    }

    @org.junit.jupiter.api.Test
    void signHintCommitsMappedSentence() throws Exception {
        String payload = "{\"type\":\"USER_MESSAGE\",\"inputMode\":\"SIGN\","
                + "\"text\":\"\",\"signDetected\":\"HELLO\"}";
        handler.drive(session, new TextMessage(payload));

        awaitFrames(3);
        assertEquals(3, frames.size(), frames.toString());
        JsonNode transcription = objectMapper.readTree(frames.get(0));
        JsonNode chunk = objectMapper.readTree(frames.get(1));
        JsonNode end = objectMapper.readTree(frames.get(2));
        assertEquals(WsMessageType.TRANSCRIPTION, transcription.get("type").asText());
        assertEquals("[Sign: HELLO] Hello", transcription.get("transcribedText").asText());
        assertEquals(WsMessageType.MENTOR_RESPONSE_CHUNK, chunk.get("type").asText());
        assertEquals(WsMessageType.MENTOR_RESPONSE_END, end.get("type").asText());
        assertTrue(llama.capturedUserText.equals("Hello"), llama.capturedUserText);
        assertTrue(messages.stream().anyMatch(m -> m.getRole().equals("USER")
                && m.getContent().equals("Hello")
                && m.getInputMode() == InputMode.SIGN));
        assertTrue(!sign.called, "Hint path has no landmarks to classify");
    }

    @org.junit.jupiter.api.Test
    void signLandmarksPreferClassificationOverHint() throws Exception {
        // Both fields sent (client transition): landmarks win over the hint.
        sign.result = new SignAgentClient.SignResult("HELP", 1.0);
        String payload = "{\"type\":\"USER_MESSAGE\",\"inputMode\":\"SIGN\","
                + "\"signDetected\":\"HELLO\",\"landmarks\":" + landmarksJson(21) + "}";
        handler.drive(session, new TextMessage(payload));

        awaitFrames(3);
        assertEquals(3, frames.size(), frames.toString());
        JsonNode transcription = objectMapper.readTree(frames.get(0));
        assertEquals(WsMessageType.TRANSCRIPTION, transcription.get("type").asText());
        assertEquals("[Sign: HELP] I need help", transcription.get("transcribedText").asText());
        assertEquals(WsMessageType.MENTOR_RESPONSE_END,
                objectMapper.readTree(frames.get(2)).get("type").asText());
        assertTrue(sign.called, "Landmarks must be classified");
        assertEquals(21, sign.captured.size());
        assertTrue(llama.capturedUserText.equals("I need help"), llama.capturedUserText);
        assertTrue(messages.stream().anyMatch(m -> m.getRole().equals("USER")
                && m.getContent().equals("I need help")
                && m.getInputMode() == InputMode.SIGN));
    }

    @org.junit.jupiter.api.Test
    void signLowConfidenceYieldsCalmError() throws Exception {
        sign.result = SignAgentClient.EMPTY;
        String payload = "{\"type\":\"USER_MESSAGE\",\"inputMode\":\"SIGN\","
                + "\"landmarks\":" + landmarksJson(21) + "}";
        handler.drive(session, new TextMessage(payload));

        awaitFrames(1);
        assertEquals(1, frames.size(), frames.toString());
        JsonNode error = objectMapper.readTree(frames.get(0));
        assertEquals(WsMessageType.ERROR, error.get("type").asText());
        assertTrue(error.get("textChunk").asText().contains("hold the sign steady"),
                frames.toString());
        assertTrue(!llama.called, "Unclear sign must not reach Llama");
        assertTrue(messages.isEmpty(), "Unclear sign must not persist messages");
    }

    @org.junit.jupiter.api.Test
    void signWithoutInputYieldsCalmError() throws Exception {
        String payload = "{\"type\":\"USER_MESSAGE\",\"inputMode\":\"SIGN\",\"text\":\"\"}";
        handler.drive(session, new TextMessage(payload));

        awaitFrames(1);
        assertEquals(1, frames.size(), frames.toString());
        JsonNode error = objectMapper.readTree(frames.get(0));
        assertEquals(WsMessageType.ERROR, error.get("type").asText());
        assertTrue(error.get("textChunk").asText().contains("hold the sign steady"),
                frames.toString());
        assertTrue(!llama.called, "Empty sign frame must not reach Llama");
        assertTrue(!sign.called, "No landmarks means no classification call");
    }

    @org.junit.jupiter.api.Test
    void signSentencesCoverAllTenLabels() {
        assertEquals("I need help", MentorOrchestrator.mapSignToSentence("HELP"));
        assertEquals("I need help", MentorOrchestrator.mapSignToSentence("I_NEED_HELP"));
        assertEquals("I am feeling sad", MentorOrchestrator.mapSignToSentence("SAD"));
        assertEquals("I am feeling stressed", MentorOrchestrator.mapSignToSentence("STRESS"));
        assertEquals("Hello", MentorOrchestrator.mapSignToSentence("HELLO"));
        assertEquals("Thank you", MentorOrchestrator.mapSignToSentence("THANK_YOU"));
        assertEquals("Yes", MentorOrchestrator.mapSignToSentence("YES"));
        assertEquals("No", MentorOrchestrator.mapSignToSentence("NO"));
        assertEquals("Please help me", MentorOrchestrator.mapSignToSentence("PLEASE"));
        assertEquals("I am feeling happy", MentorOrchestrator.mapSignToSentence("HAPPY"));
        assertEquals("Yes", MentorOrchestrator.mapSignToSentence("  yes "));
        assertEquals(null, MentorOrchestrator.mapSignToSentence("WAVE"));
        assertEquals(null, MentorOrchestrator.mapSignToSentence(null));
        assertEquals(null, MentorOrchestrator.mapSignToSentence("   "));
    }

    private static String landmarksJson(int points) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < points; i++) {
            if (i > 0) {
                json.append(",");
            }
            json.append("[0.5,0.4,0.01]");
        }
        return json.append("]").toString();
    }

    @org.junit.jupiter.api.Test
    void crisisYieldsSingleAlertWithoutBrain() throws Exception {
        String payload = "{\"type\":\"USER_MESSAGE\",\"inputMode\":\"TEXT\","
                + "\"text\":\"I want to die\"}";
        handler.drive(session, new TextMessage(payload));

        awaitFrames(1);
        assertEquals(1, frames.size(), frames.toString());
        JsonNode alert = objectMapper.readTree(frames.get(0));
        assertEquals(WsMessageType.CRISIS_ALERT, alert.get("type").asText());
        assertTrue(alert.get("textChunk").asText().contains("14416"));
        assertTrue(!llama.called, "Llama must not be called on crisis");
        assertEquals(2, messages.size());
    }

    @org.junit.jupiter.api.Test
    void brainFailureYieldsCalmError() throws Exception {
        llama.mode = StubLlama.Mode.FAILURE;
        String payload = "{\"type\":\"USER_MESSAGE\",\"inputMode\":\"TEXT\",\"text\":\"hello\"}";
        handler.drive(session, new TextMessage(payload));

        awaitFrames(1);
        assertEquals(1, frames.size(), frames.toString());
        JsonNode error = objectMapper.readTree(frames.get(0));
        assertEquals(WsMessageType.ERROR, error.get("type").asText());
        assertTrue(error.get("textChunk").asText().contains("give me a moment"));
        assertTrue(!error.get("textChunk").asText().contains("boom"));
    }

    @org.junit.jupiter.api.Test
    void foreignConversationIdIsRejected() throws Exception {
        Conversation foreign = Conversation.builder().id(UUID.randomUUID())
                .userId(OTHER_USER).title("x").build();
        conversations.put(foreign.getId(), foreign);
        String payload = "{\"type\":\"USER_MESSAGE\",\"conversationId\":\"" + foreign.getId()
                + "\",\"inputMode\":\"TEXT\",\"text\":\"hello\"}";
        handler.drive(session, new TextMessage(payload));

        awaitFrames(1);
        JsonNode error = objectMapper.readTree(frames.get(0));
        assertEquals(WsMessageType.ERROR, error.get("type").asText());
        assertTrue(!llama.called, "Llama must not be called for rejected conversation");
    }

    private void awaitFrames(int count) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (frames.size() < count && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
    }

    private WebSocketSession session(Map<String, Object> attributes) {
        return (WebSocketSession) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{WebSocketSession.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getId":
                            return "s1";
                        case "getAttributes":
                            return attributes;
                        case "isOpen":
                            return true;
                        case "sendMessage":
                            frames.add(((TextMessage) args[0]).getPayload());
                            return null;
                        case "close":
                            return null;
                        default:
                            throw new UnsupportedOperationException(method.getName());
                    }
                });
    }

    @SuppressWarnings("unchecked")
    private static <T> T fake(Class<T> type, HandlerFn fn) {
        return (T) Proxy.newProxyInstance(MentorFlowTest.class.getClassLoader(),
                new Class<?>[]{type}, (proxy, method, args) -> fn.handle(method, args));
    }

    @FunctionalInterface
    private interface HandlerFn {
        Object handle(java.lang.reflect.Method method, Object[] args) throws Throwable;
    }

    /** Exposes the protected entry point for the flow test. */
    static class OpenHandler extends MentorWebSocketHandler {
        OpenHandler(MentorOrchestrator orchestrator, ObjectMapper objectMapper) {
            super(orchestrator, objectMapper);
        }

        void drive(WebSocketSession session, TextMessage message) {
            handleTextMessage(session, message);
        }
    }

    static class StubLlama extends LlamaService {
        enum Mode {NORMAL, FAILURE}

        Mode mode = Mode.NORMAL;
        boolean called;
        String capturedPrompt;
        String capturedUserText;

        StubLlama() {
            super(null, null, "test-model");
        }

        @Override
        public Flux<String> streamResponse(String filledSystemPrompt, String emotion, String userText) {
            called = true;
            capturedPrompt = filledSystemPrompt;
            capturedUserText = userText;
            if (mode == Mode.FAILURE) {
                return Flux.error(new RuntimeException("boom-500"));
            }
            return Flux.just("He", "llo");
        }
    }

    static class StubSpeech extends SpeechAgent {
        String result = "stub transcript";
        boolean fail;

        StubSpeech() {
            super(null, null, "whisper-large-v3", 10000L);
        }

        @Override
        public String transcribe(String audioBase64, String inputMode) {
            if (fail) {
                throw new SpeechTranscriptionException("whisper-down");
            }
            return result;
        }
    }

    static class StubSign extends SignAgentClient {
        boolean called;
        List<List<Double>> captured;
        SignResult result = new SignResult("HELP", 1.0);

        StubSign() {
            super(null, null, true, 0.80, 2000L);
        }

        @Override
        public Mono<SignResult> predictSign(List<List<Double>> landmarks) {
            called = true;
            captured = landmarks;
            return Mono.just(result);
        }
    }

    static class StubMcp extends com.saarthi.ai.mcp.McpMemoryService {
        StubMcp() {
            super(null, null, null, false, 5000L, 600L);
        }

        @Override
        public java.util.Optional<String> recentMoodsCsv(java.util.UUID userId) {
            return java.util.Optional.empty();
        }
    }

    static class StubVoice extends VoiceAgent {
        boolean fail;

        StubVoice() {
            super(null, 5000L);
        }

        @Override
        public Mono<byte[]> textToSpeech(String sentence, String languageHint) {
            if (fail) {
                return Mono.empty();
            }
            return Mono.just(FAKE_AUDIO);
        }
    }
}
