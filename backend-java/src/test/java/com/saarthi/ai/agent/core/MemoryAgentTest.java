package com.saarthi.ai.agent.core;

import com.saarthi.ai.model.Conversation;
import com.saarthi.ai.model.InputMode;
import com.saarthi.ai.model.Message;
import com.saarthi.ai.model.MoodLog;
import com.saarthi.ai.model.MoodType;
import com.saarthi.ai.repository.ConversationRepository;
import com.saarthi.ai.repository.MessageRepository;
import com.saarthi.ai.repository.MoodLogRepository;
import com.saarthi.ai.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MemoryAgent persistence rules with in-memory fakes - no database, no Spring.
 * Repositories are faked with JDK dynamic proxies so the test runs anywhere.
 */
class MemoryAgentTest {

    @Test
    void saveMoodConvertsDoubleConfidenceToFloat() {
        AtomicReference<MoodLog> saved = new AtomicReference<>();
        MoodLogRepository moodLogs = fake(MoodLogRepository.class, (method, args) -> {
            if (method.getName().equals("save")) {
                saved.set((MoodLog) args[0]);
                return args[0];
            }
            throw new UnsupportedOperationException(method.getName());
        });
        MemoryAgent agent = agent(dummy(ConversationRepository.class),
                dummy(MessageRepository.class), moodLogs);

        agent.saveMood(UUID.randomUUID(), UUID.randomUUID(), MoodType.STRESSED, 0.87);

        assertEquals(MoodType.STRESSED, saved.get().getMood());
        assertEquals(0.87f, saved.get().getConfidence(), 0.0001f);
    }

    @Test
    void getOrCreateConversationReturnsOwnedConversation() {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        Conversation owned = Conversation.builder()
                .id(conversationId).userId(userId).title("old").build();
        AtomicBoolean saved = new AtomicBoolean(false);
        ConversationRepository conversations = fake(ConversationRepository.class, (method, args) -> {
            if (method.getName().equals("findById")) {
                return Optional.of(owned);
            }
            if (method.getName().equals("save")) {
                saved.set(true);
                return args[0];
            }
            throw new UnsupportedOperationException(method.getName());
        });
        MemoryAgent agent = agent(conversations, dummy(MessageRepository.class),
                dummy(MoodLogRepository.class));

        Conversation result = agent.getOrCreateConversation(userId, conversationId);

        assertSame(owned, result);
        assertTrue(!saved.get(), "Owned conversation must be reused, not re-saved");
    }

    @Test
    void getOrCreateConversationCreatesNewWhenNull() {
        UUID userId = UUID.randomUUID();
        Conversation created = Conversation.builder()
                .id(UUID.randomUUID()).userId(userId).title("New conversation").build();
        AtomicReference<Conversation> saved = new AtomicReference<>();
        ConversationRepository conversations = fake(ConversationRepository.class, (method, args) -> {
            if (method.getName().equals("save")) {
                saved.set((Conversation) args[0]);
                return created;
            }
            throw new UnsupportedOperationException(method.getName());
        });
        MemoryAgent agent = agent(conversations, dummy(MessageRepository.class),
                dummy(MoodLogRepository.class));

        Conversation result = agent.getOrCreateConversation(userId, null);

        assertSame(created, result);
        assertEquals("New conversation", saved.get().getTitle());
        assertEquals(userId, saved.get().getUserId());
    }

    @Test
    void getOrCreateConversationThrowsWhenNotOwned() {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        Conversation foreign = Conversation.builder()
                .id(conversationId).userId(UUID.randomUUID()).title("someone else").build();
        ConversationRepository conversations = fake(ConversationRepository.class, (method, args) -> {
            if (method.getName().equals("findById")) {
                return Optional.of(foreign);
            }
            throw new UnsupportedOperationException(method.getName());
        });
        MemoryAgent agent = agent(conversations, dummy(MessageRepository.class),
                dummy(MoodLogRepository.class));

        assertThrows(IllegalArgumentException.class,
                () -> agent.getOrCreateConversation(userId, conversationId));
    }

    @Test
    void getOrCreateConversationThrowsWhenMissing() {
        ConversationRepository conversations = fake(ConversationRepository.class, (method, args) -> {
            if (method.getName().equals("findById")) {
                return Optional.empty();
            }
            throw new UnsupportedOperationException(method.getName());
        });
        MemoryAgent agent = agent(conversations, dummy(MessageRepository.class),
                dummy(MoodLogRepository.class));

        assertThrows(IllegalArgumentException.class,
                () -> agent.getOrCreateConversation(UUID.randomUUID(), UUID.randomUUID()));
    }

    @Test
    void saveUserMessageParsesInputModeSafely() {
        AtomicReference<Message> saved = new AtomicReference<>();
        MessageRepository messages = fake(MessageRepository.class, (method, args) -> {
            if (method.getName().equals("save")) {
                saved.set((Message) args[0]);
                return args[0];
            }
            throw new UnsupportedOperationException(method.getName());
        });
        MemoryAgent agent = agent(dummy(ConversationRepository.class), messages,
                dummy(MoodLogRepository.class));

        agent.saveUserMessage(UUID.randomUUID(), "hello", "VOICE");
        assertEquals("USER", saved.get().getRole());
        assertEquals("hello", saved.get().getContent());
        assertEquals(InputMode.VOICE, saved.get().getInputMode());

        agent.saveUserMessage(UUID.randomUUID(), "hello", "bogus-mode");
        assertEquals(InputMode.TEXT, saved.get().getInputMode());

        agent.saveUserMessage(UUID.randomUUID(), "hello", null);
        assertEquals(InputMode.TEXT, saved.get().getInputMode());
    }

    @Test
    void saveMentorMessageSavesMentorRole() {
        AtomicReference<Message> saved = new AtomicReference<>();
        MessageRepository messages = fake(MessageRepository.class, (method, args) -> {
            if (method.getName().equals("save")) {
                saved.set((Message) args[0]);
                return args[0];
            }
            throw new UnsupportedOperationException(method.getName());
        });
        MemoryAgent agent = agent(dummy(ConversationRepository.class), messages,
                dummy(MoodLogRepository.class));

        agent.saveMentorMessage(UUID.randomUUID(), "full reply");

        assertEquals("MENTOR", saved.get().getRole());
        assertEquals("full reply", saved.get().getContent());
        assertEquals(InputMode.TEXT, saved.get().getInputMode());
    }

    @Test
    void getMoodHistoryClampsWindowToOneToThirtyDays() {
        AtomicReference<java.time.LocalDateTime> cutoff = new AtomicReference<>();
        MoodLogRepository moods = fake(MoodLogRepository.class, (method, args) -> {
            if (method.getName().equals("findByUserIdAndLoggedAtAfterOrderByLoggedAtDesc")) {
                cutoff.set((java.time.LocalDateTime) args[1]);
                return java.util.List.of();
            }
            throw new UnsupportedOperationException(method.getName());
        });
        MemoryAgent agent = agent(dummy(ConversationRepository.class),
                dummy(MessageRepository.class), moods);

        agent.getMoodHistory(UUID.randomUUID(), 500);
        long daysHigh = java.time.Duration.between(cutoff.get(), java.time.LocalDateTime.now())
                .toDays();
        assertTrue(daysHigh >= 29 && daysHigh <= 30, "500 days must clamp to 30");

        agent.getMoodHistory(UUID.randomUUID(), -3);
        long daysLow = java.time.Duration.between(cutoff.get(), java.time.LocalDateTime.now())
                .toDays();
        assertTrue(daysLow >= 0 && daysLow <= 1, "Negative days must clamp to 1");
    }

    @Test
    void saveCheckinPersistsMoodWithoutConversation() {
        AtomicReference<MoodLog> saved = new AtomicReference<>();
        MoodLogRepository moods = fake(MoodLogRepository.class, (method, args) -> {
            if (method.getName().equals("save")) {
                saved.set((MoodLog) args[0]);
                return args[0];
            }
            throw new UnsupportedOperationException(method.getName());
        });
        MemoryAgent agent = agent(dummy(ConversationRepository.class),
                dummy(MessageRepository.class), moods);

        agent.saveCheckin(UUID.randomUUID(), "stressed", "  exams again  ");

        assertEquals(MoodType.STRESSED, saved.get().getMood());
        assertEquals("exams again", saved.get().getNote());
        assertEquals(null, saved.get().getConversationId());
    }

    @Test
    void saveCheckinRejectsUnknownMood() {
        MemoryAgent agent = agent(dummy(ConversationRepository.class),
                dummy(MessageRepository.class), dummy(MoodLogRepository.class));

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> agent.saveCheckin(UUID.randomUUID(), "ecstatic", null));
        assertEquals("Please choose a valid mood.", thrown.getMessage());
    }

    @Test
    void getRecentConversationsShapesPreviews() {
        UUID userId = UUID.randomUUID();
        Conversation first = Conversation.builder()
                .id(UUID.randomUUID()).userId(userId).title("First").build();
        Conversation second = Conversation.builder()
                .id(UUID.randomUUID()).userId(userId).title("Second").build();
        ConversationRepository conversations = fake(ConversationRepository.class, (method, args) -> {
            if (method.getName().equals("findByUserIdOrderByStartedAtDesc")) {
                return java.util.List.of(first, second);
            }
            throw new UnsupportedOperationException(method.getName());
        });
        MessageRepository messages = fake(MessageRepository.class, (method, args) -> {
            if (method.getName().equals("findFirstByConversationIdOrderByCreatedAtDesc")) {
                if (args[0].equals(first.getId())) {
                    return Optional.of(Message.builder().content("x".repeat(200)).build());
                }
                return Optional.empty();
            }
            throw new UnsupportedOperationException(method.getName());
        });
        MemoryAgent agent = agent(conversations, messages, dummy(MoodLogRepository.class));

        java.util.List<MemoryAgent.ConversationSummary> rows =
                agent.getRecentConversations(userId, 10);

        assertEquals(2, rows.size());
        assertEquals(first.getId(), rows.get(0).id());
        assertEquals(121, rows.get(0).preview().length());
        assertTrue(rows.get(0).preview().endsWith("…"));
        assertEquals("", rows.get(1).preview());
    }

    @Test
    void getRecentMoodsPrefersMcpOverJpa() {
        MemoryAgent agent = new MemoryAgent(dummy(ConversationRepository.class),
                dummy(MessageRepository.class), dummy(MoodLogRepository.class),
                dummy(UserRepository.class), new StubMcp(java.util.Optional.of("STRESSED")));

        // Dummy repos throw on any call - reaching JPA would explode here.
        assertEquals("STRESSED", agent.getRecentMoods(UUID.randomUUID()));
    }

    @Test
    void getRecentMoodsFallsBackToJpaWhenMcpMisses() {
        MoodLogRepository moods = fake(MoodLogRepository.class, (method, args) -> {
            if (method.getName().equals("findByUserIdOrderByLoggedAtDesc")) {
                return java.util.List.of(
                        MoodLog.builder().mood(MoodType.TIRED).build());
            }
            throw new UnsupportedOperationException(method.getName());
        });
        MemoryAgent agent = new MemoryAgent(dummy(ConversationRepository.class),
                dummy(MessageRepository.class), moods,
                dummy(UserRepository.class), new StubMcp(java.util.Optional.empty()));

        assertEquals("TIRED", agent.getRecentMoods(UUID.randomUUID()));
    }

    static class StubMcp extends com.saarthi.ai.mcp.McpMemoryService {
        private final java.util.Optional<String> script;

        StubMcp(java.util.Optional<String> script) {
            super(null, null, null, true, 5000L, 600L);
            this.script = script;
        }

        @Override
        public java.util.Optional<String> recentMoodsCsv(UUID userId) {
            return script;
        }
    }

    private static MemoryAgent agent(ConversationRepository conversations,
                                     MessageRepository messages, MoodLogRepository moods) {
        return new MemoryAgent(conversations, messages, moods, dummy(UserRepository.class),
                new StubMcp(java.util.Optional.empty()));
    }

    @FunctionalInterface
    private interface Handler {
        Object handle(Method method, Object[] args) throws Throwable;
    }

    @SuppressWarnings("unchecked")
    private static <T> T fake(Class<T> type, Handler handler) {
        return (T) Proxy.newProxyInstance(MemoryAgentTest.class.getClassLoader(),
                new Class<?>[]{type}, (proxy, method, args) -> handler.handle(method, args));
    }

    private static <T> T dummy(Class<T> type) {
        return fake(type, (method, args) -> {
            throw new UnsupportedOperationException("Unexpected call: " + method.getName());
        });
    }
}
