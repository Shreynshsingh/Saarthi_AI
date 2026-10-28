package com.saarthi.ai.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Endpoint registration: exact path, exact single origin - never "*".
 */
class WebSocketConfigTest {

    @Test
    void registersMentorEndpointWithExactOriginOnly() {
        AtomicReference<String[]> paths = new AtomicReference<>();
        AtomicReference<String[]> origins = new AtomicReference<>();
        AtomicReference<Object> self = new AtomicReference<>();
        WebSocketHandlerRegistration registration = (WebSocketHandlerRegistration) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{WebSocketHandlerRegistration.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("setAllowedOrigins")) {
                        origins.set((String[]) args[0]);
                        return self.get();
                    }
                    if (method.getName().equals("addInterceptors")) {
                        return self.get();
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        self.set(registration);
        WebSocketHandlerRegistry registry = (WebSocketHandlerRegistry) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{WebSocketHandlerRegistry.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("addHandler")) {
                        paths.set((String[]) args[1]);
                        return registration;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });

        new WebSocketConfig(null, null, "http://localhost:3000").registerWebSocketHandlers(registry);

        assertArrayEquals(new String[]{"/ws/mentor"}, paths.get());
        assertArrayEquals(new String[]{"http://localhost:3000"}, origins.get());
    }

    @Test
    void containerBuffersAcceptVoicePayloads() {
        var container = new WebSocketConfig(null, null, "http://localhost:3000").webSocketContainer();

        assertEquals(2 * 1024 * 1024, container.getMaxTextMessageBufferSize());
        assertEquals(2 * 1024 * 1024, container.getMaxBinaryMessageBufferSize());
    }
}
