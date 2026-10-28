package com.saarthi.ai.config;

import com.saarthi.ai.repository.UserRepository;
import com.saarthi.ai.service.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/**
 * WebSocket handshake authentication. Browsers cannot send Authorization headers
 * on WebSocket connect, so the JWT arrives as a {@code ?token=} query param.
 *
 * Valid token + existing user -&gt; stores the user UUID in handshake attributes
 * (copied to the session) under {@link #ATTR_USER_ID}.
 *
 * Missing/invalid token or deleted account -&gt; stores nothing. The handshake is
 * deliberately NOT rejected here: the handler closes such sessions with the
 * documented 4401 code instead (a rejected handshake produces no close frame,
 * and Module 2 requires clients to observe 4401). Never logs the token itself.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_USER_ID = "userId";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = extractToken(request);
        if (token != null && jwtService.isTokenValid(token)) {
            UUID userId = jwtService.extractUserId(token);
            // Token cryptographically valid, but the account may have been deleted.
            if (userRepository.existsById(userId)) {
                attributes.put(ATTR_USER_ID, userId);
                return true;
            }
            log.warn("[WebSocket] Handshake for deleted account");
            return true;
        }
        log.warn("[WebSocket] Handshake without valid token");
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // No-op.
    }

    private String extractToken(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            String token = servletRequest.getServletRequest().getParameter("token");
            if (token != null && !token.isBlank()) {
                return token.trim();
            }
        }
        // Fallback: parse the raw query string (also covers non-servlet runtimes).
        URI uri = request.getURI();
        return uri == null ? null : fromQuery(uri.getQuery());
    }

    private String fromQuery(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            String name = eq < 0 ? pair : pair.substring(0, eq);
            if (name.equals("token")) {
                String value = eq < 0 ? "" : pair.substring(eq + 1);
                try {
                    value = URLDecoder.decode(value, StandardCharsets.UTF_8);
                } catch (IllegalArgumentException e) {
                    return null;
                }
                return value.isBlank() ? null : value;
            }
        }
        return null;
    }
}
