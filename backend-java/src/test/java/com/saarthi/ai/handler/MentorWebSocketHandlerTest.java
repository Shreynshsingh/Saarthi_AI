package com.saarthi.ai.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saarthi.ai.config.AuthHandshakeInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Handler session lifecycle with a faked WebSocketSession.
 * The orchestrator is never touched here (constructor arg unused on this path).
 */
class MentorWebSocketHandlerTest {

    private final MentorWebSocketHandler handler =
            new MentorWebSocketHandler(null, new ObjectMapper());

    @Test
    void missingUserIdCloses4401() throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        AtomicReference<CloseStatus> closed = new AtomicReference<>();

        handler.afterConnectionEstablished(session(attributes, closed));

        assertEquals(4401, closed.get().getCode());
        assertEquals("Unauthorized", closed.get().getReason());
    }

    @Test
    void authenticatedSessionInitializesAndCleansUp() throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(AuthHandshakeInterceptor.ATTR_USER_ID, UUID.randomUUID());
        AtomicReference<CloseStatus> closed = new AtomicReference<>();
        WebSocketSession session = session(attributes, closed);

        handler.afterConnectionEstablished(session);

        assertTrue(attributes.get("executor") instanceof ExecutorService);
        assertTrue(closed.get() == null, "Authenticated session must not be closed");

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        assertTrue(((ExecutorService) attributes.get("executor")).isShutdown());
    }

    private WebSocketSession session(Map<String, Object> attributes,
                                     AtomicReference<CloseStatus> closed) {
        return (WebSocketSession) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{WebSocketSession.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getId":
                            return "s1";
                        case "getAttributes":
                            return attributes;
                        case "close":
                            closed.set((CloseStatus) args[0]);
                            return null;
                        case "isOpen":
                            return true;
                        default:
                            throw new UnsupportedOperationException(method.getName());
                    }
                });
    }
}
