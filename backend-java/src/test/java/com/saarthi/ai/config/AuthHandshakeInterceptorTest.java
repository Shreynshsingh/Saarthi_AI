package com.saarthi.ai.config;

import com.saarthi.ai.model.User;
import com.saarthi.ai.repository.UserRepository;
import com.saarthi.ai.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServletServerHttpRequest;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Handshake authentication: real JWTs through the real JwtService, repository faked.
 * No server, no network.
 */
class AuthHandshakeInterceptorTest {

    private static final String SECRET = "test-secret-that-is-at-least-32-bytes-long!!";

    private final JwtService jwtService = new JwtService(SECRET, 3600000);
    private final UUID userId = UUID.randomUUID();

    @Test
    void validTokenAndExistingUserStoresUserId() throws Exception {
        AuthHandshakeInterceptor interceptor = interceptor(true);
        Map<String, Object> attributes = new HashMap<>();

        boolean result = interceptor.beforeHandshake(
                servletRequest(signedToken()), null, null, attributes);

        assertTrue(result);
        assertEquals(userId, attributes.get(AuthHandshakeInterceptor.ATTR_USER_ID));
    }

    @Test
    void missingTokenStoresNothing() throws Exception {
        AuthHandshakeInterceptor interceptor = interceptor(true);
        Map<String, Object> attributes = new HashMap<>();

        interceptor.beforeHandshake(servletRequest(null), null, null, attributes);

        assertFalse(attributes.containsKey(AuthHandshakeInterceptor.ATTR_USER_ID));
    }

    @Test
    void garbageTokenStoresNothing() throws Exception {
        AuthHandshakeInterceptor interceptor = interceptor(true);
        Map<String, Object> attributes = new HashMap<>();

        interceptor.beforeHandshake(servletRequest("not-a-jwt"), null, null, attributes);

        assertFalse(attributes.containsKey(AuthHandshakeInterceptor.ATTR_USER_ID));
    }

    @Test
    void deletedAccountStoresNothing() throws Exception {
        AuthHandshakeInterceptor interceptor = interceptor(false);
        Map<String, Object> attributes = new HashMap<>();

        interceptor.beforeHandshake(servletRequest(signedToken()), null, null, attributes);

        assertFalse(attributes.containsKey(AuthHandshakeInterceptor.ATTR_USER_ID));
    }

    @Test
    void uriFallbackParsesToken() throws Exception {
        AuthHandshakeInterceptor interceptor = interceptor(true);
        Map<String, Object> attributes = new HashMap<>();
        URI uri = new URI("ws://localhost/ws/mentor?token=" + signedToken());
        ServerHttpRequest request = (ServerHttpRequest) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{ServerHttpRequest.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getURI")) {
                        return uri;
                    }
                    if (method.getName().equals("getMethod")) {
                        return org.springframework.http.HttpMethod.GET;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });

        interceptor.beforeHandshake(request, null, null, attributes);

        assertEquals(userId, attributes.get(AuthHandshakeInterceptor.ATTR_USER_ID));
    }

    private AuthHandshakeInterceptor interceptor(boolean userExists) {
        UserRepository users = (UserRepository) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{UserRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("existsById")) {
                        return userExists;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        return new AuthHandshakeInterceptor(jwtService, users);
    }

    private String signedToken() {
        User user = User.builder().id(userId).name("Aarav")
                .email("a@t.c").passwordHash("x").build();
        return jwtService.generateToken(user);
    }

    private ServletServerHttpRequest servletRequest(String token) {
        HttpServletRequest httpRequest = (HttpServletRequest) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{HttpServletRequest.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getParameter")
                            && args.length == 1 && args[0].equals("token")) {
                        return token;
                    }
                    if (method.getName().equals("getMethod")) {
                        return "GET";
                    }
                    if (method.getName().equals("getRequestURL")) {
                        return new StringBuffer("http://localhost/ws/mentor");
                    }
                    if (method.getName().equals("getQueryString")) {
                        return token == null ? null : "token=" + token;
                    }
                    if (method.getName().equals("getHeaderNames")
                            || method.getName().equals("getParameterNames")) {
                        return Collections.emptyEnumeration();
                    }
                    if (method.getName().equals("getParameterMap")) {
                        return Map.of();
                    }
                    Class<?> rt = method.getReturnType();
                    if (rt == boolean.class) {
                        return false;
                    }
                    if (rt == int.class) {
                        return 0;
                    }
                    if (rt == long.class) {
                        return 0L;
                    }
                    return null;
                });
        return new ServletServerHttpRequest(httpRequest);
    }
}
