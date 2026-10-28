package com.saarthi.ai.config;

import com.saarthi.ai.handler.MentorWebSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * Registers the single live-session endpoint. Auth happens in
 * {@link AuthHandshakeInterceptor} (query-param JWT validated during the
 * handshake), which is why SecurityConfig permits /ws/** at HTTP level.
 * Origin is restricted to the configured frontend - never "*".
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final MentorWebSocketHandler mentorWebSocketHandler;
    private final AuthHandshakeInterceptor authHandshakeInterceptor;
    private final String frontendUrl;

    public WebSocketConfig(
            MentorWebSocketHandler mentorWebSocketHandler,
            AuthHandshakeInterceptor authHandshakeInterceptor,
            @Value("${saarthi.security.cors.allowed-origin:http://localhost:3000}") String frontendUrl) {
        this.mentorWebSocketHandler = mentorWebSocketHandler;
        this.authHandshakeInterceptor = authHandshakeInterceptor;
        this.frontendUrl = frontendUrl;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(mentorWebSocketHandler, "/ws/mentor")
                .addInterceptors(authHandshakeInterceptor)
                .setAllowedOrigins(frontendUrl);
    }

    /**
     * Voice messages carry Base64 audio (~440KB for 5s of webm, more for wav);
     * the container's default buffer would reject them mid-handshake. 2MB text
     * and binary buffers accept a full voice turn without opening the door to
     * unbounded payloads.
     */
    @Bean
    public ServletServerContainerFactoryBean webSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(2 * 1024 * 1024);
        container.setMaxBinaryMessageBufferSize(2 * 1024 * 1024);
        return container;
    }
}
