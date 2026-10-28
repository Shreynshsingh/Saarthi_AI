package com.saarthi.ai.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saarthi.ai.agent.orchestrator.MentorOrchestrator;
import com.saarthi.ai.config.AuthHandshakeInterceptor;
import com.saarthi.ai.dto.AgentContext;
import com.saarthi.ai.dto.ChatRequest;
import com.saarthi.ai.dto.ChatResponse;
import com.saarthi.ai.dto.WsMessageType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Single persistent connection per live mentor session: /ws/mentor?token=JWT.
 *
 * SECURITY: the userId comes ONLY from session attributes set by
 * {@link AuthHandshakeInterceptor} during the handshake - a userId in a message
 * payload would never be trusted. Sessions without it are closed with 4401.
 *
 * CONCURRENCY: one single-thread (virtual) executor per session processes that
 * session's messages strictly in order; sends are synchronized on the session.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MentorWebSocketHandler extends TextWebSocketHandler {

    private static final CloseStatus UNAUTHORIZED = new CloseStatus(4401, "Unauthorized");
    private static final String ATTR_EXECUTOR = "executor";

    private final MentorOrchestrator mentorOrchestrator;
    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<String, ExecutorService> sessionExecutors = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        UUID userId = (UUID) session.getAttributes().get(AuthHandshakeInterceptor.ATTR_USER_ID);
        if (userId == null) {
            closeQuietly(session, UNAUTHORIZED);
            return;
        }
        ExecutorService executor =
                Executors.newSingleThreadExecutor(Thread.ofVirtual().factory());
        session.getAttributes().put(ATTR_EXECUTOR, executor);
        sessionExecutors.put(session.getId(), executor);
        log.info("[WebSocket] User {} connected", userId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        ExecutorService executor = (ExecutorService) session.getAttributes().get(ATTR_EXECUTOR);
        UUID userId = (UUID) session.getAttributes().get(AuthHandshakeInterceptor.ATTR_USER_ID);
        if (executor == null || executor.isShutdown() || userId == null) {
            return; // Connection is closing; ignore late messages.
        }
        String payload = message.getPayload();
        executor.submit(() -> handlePayload(session, userId, payload));
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("[WebSocket] Transport error: session={} error={}",
                session.getId(), exception.getMessage());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        UUID userId = (UUID) session.getAttributes().get(AuthHandshakeInterceptor.ATTR_USER_ID);
        cleanup(session);
        if (userId != null) {
            log.info("[WebSocket] User {} disconnected", userId);
        } else {
            log.info("[WebSocket] Unauthenticated connection {} closed", session.getId());
        }
    }

    private void handlePayload(WebSocketSession session, UUID userId, String payload) {
        ChatRequest incoming;
        try {
            incoming = objectMapper.readValue(payload, ChatRequest.class);
        } catch (JsonProcessingException e) {
            sendSync(session, chatError(
                    "I couldn't understand that message. Could you please send it again?"));
            return;
        }
        if (incoming == null || !WsMessageType.USER_MESSAGE.equals(incoming.type())) {
            sendSync(session, chatError(
                    "I couldn't understand that message. Could you please send it again?"));
            return;
        }
        String inputMode = incoming.inputMode() == null ? "TEXT" : incoming.inputMode().trim().toUpperCase();
        if (!"TEXT".equals(inputMode) && !"VOICE".equals(inputMode) && !"SIGN".equals(inputMode)) {
            sendSync(session, chatError(
                    "I couldn't understand that message. Could you please send it again?"));
            return;
        }
        // Blank text / missing audio pass through: the orchestrator owns input errors.
        String text = incoming.text() == null ? "" : incoming.text().trim();
        AgentContext context = new AgentContext(
                incoming.conversationId(), userId, text, null,
                incoming.emotion(), incoming.emotionConfidence(), inputMode, incoming.audioBase64());
        try {
            mentorOrchestrator.processUserMessage(context, incoming)
                    .doOnNext(msg -> sendSync(session, msg))
                    .blockLast();
        } catch (Exception e) {
            log.error("[WebSocket] Processing failed: user={}", userId, e);
            sendSync(session, chatError(
                    "I'm here with you. Please give me a moment and try again."));
        }
    }

    private ChatResponse chatError(String message) {
        return new ChatResponse(WsMessageType.ERROR, message, null, null, null, true, false);
    }

    private void sendSync(WebSocketSession session, ChatResponse msg) {
        try {
            String json = objectMapper.writeValueAsString(msg);
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(json));
                }
            }
        } catch (IOException e) {
            log.warn("[WebSocket] Send failed: session={}", session.getId());
        }
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (IOException e) {
            log.warn("[WebSocket] Close failed: session={}", session.getId());
        }
    }

    private void cleanup(WebSocketSession session) {
        ExecutorService executor = sessionExecutors.remove(session.getId());
        if (executor != null) {
            executor.shutdownNow();
        }
    }
}
