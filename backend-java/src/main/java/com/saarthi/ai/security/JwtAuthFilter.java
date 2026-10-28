package com.saarthi.ai.security;

import com.saarthi.ai.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

/**
 * SECURITY DESIGN (see docs 12_SECURITY_AND_AUTHENTICATION):
 * <ul>
 *   <li>Runs once per request, BEFORE UsernamePasswordAuthenticationFilter.</li>
 *   <li>Reads identity ONLY from the Authorization Bearer token - a userId sent
 *       in a request body is never trusted (checked in every future controller
 *       via SecurityContext instead).</li>
 *   <li>Missing/invalid tokens simply leave the context empty; the authorization
 *       rules in SecurityConfig then reject protected paths with 401. No redirect,
 *       no session is ever created (stateless API).</li>
 *   <li>Principal is the user UUID, so downstream code needs no extra DB lookup
 *       to scope queries to the authenticated user.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            if (!token.isEmpty() && jwtService.isTokenValid(token)) {
                // Token content itself is never logged.
                UUID userId = jwtService.extractUserId(token);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        filterChain.doFilter(request, response);
    }
}
