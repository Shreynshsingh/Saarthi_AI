package com.saarthi.ai.dto;

/**
 * Standard envelope for every REST response:
 * {@code { "success": true, "message": "...", "data": {...} }}.
 */
public record ApiResponse<T>(
        boolean success,
        String message,
        T data
) {
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null);
    }
}
