package com.saarthi.ai.dto;

import java.util.UUID;

/**
 * Returned inside {@link ApiResponse#data} on successful login.
 * Never contains password data.
 */
public record AuthResponse(
        String token,
        UUID userId,
        String name
) {
}
