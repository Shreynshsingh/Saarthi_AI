package com.saarthi.ai.exception;

/**
 * Thrown with deliberate user-safe messages for absent resources AND cross-user
 * access (a 404 reveals nothing about someone else's stream or mentor).
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
