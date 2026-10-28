package com.saarthi.ai.service;

import com.saarthi.ai.dto.AuthResponse;
import com.saarthi.ai.dto.LoginRequest;
import com.saarthi.ai.dto.RegisterRequest;
import com.saarthi.ai.model.User;
import com.saarthi.ai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration and login. Passwords are BCrypt-hashed before persistence and
 * never appear in logs or API responses.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public User register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already exists");
        }
        User user = User.builder()
                .name(request.name().trim())
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .preferredLanguage(request.preferredLanguage())
                .build();
        User saved = userRepository.save(user);
        log.info("User registered: id={} email={}", saved.getId(), saved.getEmail());
        return saved;
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();
        User user = userRepository.findByEmail(email)
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        log.info("User logged in: id={}", user.getId());
        return new AuthResponse(jwtService.generateToken(user), user.getId(), user.getName());
    }
}
