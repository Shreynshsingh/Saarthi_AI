package com.saarthi.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Dashboard mood check-in. Unknown mood labels fail in the service with a
 * calm 400 (see GlobalExceptionHandler); blank labels fail here.
 */
public record CheckinRequest(@NotBlank(message = "mood must not be blank") String mood,
                             @Size(max = 500, message = "note must be under 500 characters") String note) {
}
