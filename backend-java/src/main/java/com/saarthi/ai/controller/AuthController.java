package com.saarthi.ai.controller;

import com.saarthi.ai.dto.ApiResponse;
import com.saarthi.ai.dto.AuthResponse;
import com.saarthi.ai.dto.LoginRequest;
import com.saarthi.ai.dto.RegisterRequest;
import com.saarthi.ai.model.User;
import com.saarthi.ai.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Public authentication endpoints. Responses never include password data.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Map<String, Object>>> register(
            @Valid @RequestBody RegisterRequest request) {
        User user = authService.register(request);
        Map<String, Object> data = Map.of(
                "id", user.getId(),
                "name", user.getName(),
                "email", user.getEmail()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("User registered successfully", data));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        log.debug("Login attempt for email={}", request.email());
        AuthResponse auth = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful", auth));
    }
}
