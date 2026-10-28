package com.saarthi.ai.agent.core;

import com.saarthi.ai.mcp.McpMemoryService;
import com.saarthi.ai.model.Conversation;
import com.saarthi.ai.model.InputMode;
import com.saarthi.ai.model.Message;
import com.saarthi.ai.model.MoodLog;
import com.saarthi.ai.model.MoodType;
import com.saarthi.ai.model.User;
import com.saarthi.ai.repository.ConversationRepository;
import com.saarthi.ai.repository.MessageRepository;
import com.saarthi.ai.repository.MoodLogRepository;
import com.saarthi.ai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Persistence + short-term context. Feeds Llama the user's recent moods and
 * owns every write to conversations, messages, and mood_logs.
 * (May delegate context fetching to a sidecar in a later phase;
 * the orchestrator API stays the same.)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryAgent {

    private static final int RECENT_MOOD_COUNT = 3;

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final MoodLogRepository moodLogRepository;
    private final UserRepository userRepository;
    private final McpMemoryService mcpMemoryService;

    /**
     * Returns the requested conversation only if it exists AND belongs to the user.
     * A null id starts a fresh conversation. Anything else throws - never allows
     * accessing another user's conversation, and the error reveals nothing about
     * which ids exist.
     */
    @Transactional
    public Conversation getOrCreateConversation(UUID userId, UUID conversationId) {
        if (conversationId == null) {
            return conversationRepository.save(
                    Conversation.builder().userId(userId).title("New conversation").build());
        }
        return conversationRepository.findById(conversationId)
                .filter(conversation -> userId.equals(conversation.getUserId()))
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));
    }

    @Transactional(readOnly = true)
    public Optional<User> findUser(UUID userId) {
        return userRepository.findById(userId);
    }

    @Transactional(readOnly = true)
    public String getRecentMoods(UUID userId) {
        // MCP-first (Module 9): the context Llama sees preferably comes from
        // saarthi-mcp-server. Any MCP miss - disabled, down, slow, malformed -
        // falls through to the direct JPA query below, which stays as the
        // proven path until MCP is stable (13_MCP_CONNECTION.md section 13).
        String viaMcp = mcpMemoryService.recentMoodsCsv(userId).orElse(null);
        if (viaMcp != null) {
            return viaMcp;
        }
        List<MoodLog> recent = moodLogRepository.findByUserIdOrderByLoggedAtDesc(
                userId, PageRequest.of(0, RECENT_MOOD_COUNT));
        if (recent.isEmpty()) {
            return "";
        }
        return recent.stream()
                .map(log -> log.getMood().name())
                .collect(Collectors.joining(", "));
    }

    /**
     * Dashboard mood history, newest first. Days clamps to 1..30 so a client
     * can never request an unbounded scan. MCP-ready: mirrors the
     * get_recent_moods tool from 13_MCP_CONNECTION.md - a future MCP server
     * delegates to this method 1:1.
     */
    @Transactional(readOnly = true)
    public List<MoodLog> getMoodHistory(UUID userId, int days) {
        int window = Math.min(30, Math.max(1, days));
        return moodLogRepository.findByUserIdAndLoggedAtAfterOrderByLoggedAtDesc(
                userId, LocalDateTime.now().minusDays(window));
    }

    /**
     * Dashboard check-in: a mood without a conversation. Unknown labels throw
     * IllegalArgumentException with a user-safe message (rendered as 400).
     */
    @Transactional
    public MoodLog saveCheckin(UUID userId, String rawMood, String note) {
        MoodType mood = parseEmotion(rawMood)
                .orElseThrow(() -> new IllegalArgumentException("Please choose a valid mood."));
        String trimmed = note == null ? null : note.trim();
        if (trimmed != null && trimmed.length() > 500) {
            trimmed = trimmed.substring(0, 500);
        }
        if (trimmed != null && trimmed.isEmpty()) {
            trimmed = null;
        }
        return moodLogRepository.save(MoodLog.builder()
                .userId(userId)
                .mood(mood)
                .note(trimmed)
                .build());
    }

    /**
     * Recent conversations with a latest-message preview each, newest first.
     * One extra indexed lookup per conversation, capped to 20 rows - cheap
     * enough for a dashboard without a join view. Empty preview when a
     * conversation has no messages yet.
     */
    @Transactional(readOnly = true)
    public List<ConversationSummary> getRecentConversations(UUID userId, int limit) {
        int count = Math.min(20, Math.max(1, limit));
        return conversationRepository.findByUserIdOrderByStartedAtDesc(userId).stream()
                .limit(count)
                .map(conversation -> {
                    String preview = messageRepository
                            .findFirstByConversationIdOrderByCreatedAtDesc(conversation.getId())
                            .map(Message::getContent).orElse("");
                    if (preview.length() > 120) {
                        preview = preview.substring(0, 120) + "…";
                    }
                    return new ConversationSummary(conversation.getId(), conversation.getTitle(),
                            conversation.getStartedAt(), preview);
                })
                .collect(Collectors.toList());
    }

    /** Dashboard row: identity, title, start, and latest-message preview. */
    public record ConversationSummary(UUID id, String title, LocalDateTime startedAt, String preview) {
    }

    @Transactional
    public void saveUserMessage(UUID conversationId, String content, String inputMode) {
        messageRepository.save(Message.builder()
                .conversationId(conversationId)
                .role("USER")
                .content(content)
                .inputMode(parseInputMode(inputMode))
                .build());
    }

    @Transactional
    public void saveMentorMessage(UUID conversationId, String fullContent) {
        messageRepository.save(Message.builder()
                .conversationId(conversationId)
                .role("MENTOR")
                .content(fullContent)
                .inputMode(InputMode.TEXT)
                .build());
    }

    /**
     * Parses a client-sent input mode leniently. Null, blank, or unknown values
     * fall back to TEXT - persistence never crashes on bad input.
     */
    private static InputMode parseInputMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return InputMode.TEXT;
        }
        try {
            return InputMode.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("[MemoryAgent] Unknown input mode received, defaulting to TEXT");
            return InputMode.TEXT;
        }
    }

    @Transactional
    public void saveMood(UUID userId, UUID conversationId, MoodType mood, Double confidence) {
        // AgentContext carries Double (wire contract); the mood_logs column is FLOAT.
        Float confidenceFloat = confidence == null ? null : confidence.floatValue();
        moodLogRepository.save(MoodLog.builder()
                .userId(userId)
                .conversationId(conversationId)
                .mood(mood)
                .confidence(confidenceFloat)
                .build());
    }

    /**
     * Parses a client-sent emotion label leniently. Unknown labels yield empty -
     * the conversation continues with no emotion rather than failing.
     */
    public static Optional<MoodType> parseEmotion(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(MoodType.valueOf(raw.trim().toUpperCase()));
        } catch (IllegalArgumentException e) {
            String label = raw.trim();
            log.warn("[MemoryAgent] Unknown emotion label received: {}",
                    label.length() > 32 ? label.substring(0, 32) : label);
            return Optional.empty();
        }
    }
}
