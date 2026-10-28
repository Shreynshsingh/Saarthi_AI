package com.saarthi.ai.exception;

import com.saarthi.ai.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates internal failures into the standard {@link ApiResponse} envelope.
 * SECURITY: never returns stack traces, SQL errors, or internal messages.
 * Only pre-approved, user-safe messages leave this class.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Invalid request");
        log.warn("Validation failed: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(message));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(IllegalArgumentException ex) {
        // Only thrown with deliberate user-safe messages (e.g. duplicate email).
        log.warn("Bad request: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(DataIntegrityViolationException ex) {
        // Covers the register race: existsByEmail passed, unique constraint hit.
        // Root cause is logged server-side; client gets a safe message.
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Email already exists"));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(com.saarthi.ai.agent.action.avatar.AvatarUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleAvatarUnavailable(
            com.saarthi.ai.agent.action.avatar.AvatarUnavailableException ex) {
        // Provider-side failure: calm message, the frontend falls back to photo + voice.
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleTooLarge(
            org.springframework.web.multipart.MaxUploadSizeExceededException ex) {
        log.warn("Upload exceeded size limit");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("That photo is too large — please use one under 5MB."));
    }

    @ExceptionHandler(org.springframework.web.multipart.MultipartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMultipart(
            org.springframework.web.multipart.MultipartException ex) {
        log.warn("Multipart request failed: {}", ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("That upload didn't come through — please try again."));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
        // Generic message by design: never reveal whether email or password was wrong.
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Invalid email or password"));
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(
            org.springframework.web.servlet.resource.NoResourceFoundException ex) {
        // Missing static file (e.g. unknown upload URL) is a 404, never a 500.
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("That file isn't here."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Something went wrong. Please try again later."));
    }
}
