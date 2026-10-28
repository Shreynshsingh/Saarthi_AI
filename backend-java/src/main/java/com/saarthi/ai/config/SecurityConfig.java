package com.saarthi.ai.config;

import com.saarthi.ai.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * SECURITY DESIGN (see docs 12_SECURITY_AND_AUTHENTICATION):
 * <ul>
 *   <li>Stateless JWT API: CSRF disabled (no cookies/sessions to forge),
 *       session creation disabled, one BCrypt(12) PasswordEncoder.</li>
 *   <li>Public surface is minimal: auth endpoints + actuator health.
 *       /ws/** is permitted here because the WebSocket handshake validates
 *       the JWT itself (query param) in a later module - browsers cannot
 *       send Authorization headers on WebSocket connect.</li>
 *   <li>JwtAuthFilter runs before the standard auth filter so the
 *       SecurityContext holds the user UUID for all downstream code.</li>
 *   <li>401/403 responses use the same JSON envelope as the REST API instead
 *       of Spring's default empty body, so the frontend handles them uniformly.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**", "/actuator/health").permitAll()
                        // Mentor uploads: UUID filenames are unguessable, and D-ID's
                        // cloud must fetch them without our JWT. Writes stay authenticated.
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/uploads/**").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, e) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.getWriter().write(
                                    "{\"success\":false,\"message\":\"Unauthorized\",\"data\":null}");
                        })
                        .accessDeniedHandler((request, response, e) -> {
                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.getWriter().write(
                                    "{\"success\":false,\"message\":\"Forbidden\",\"data\":null}");
                        })
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
