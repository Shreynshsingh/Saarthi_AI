package com.saarthi.ai.agent.action.avatar;

/**
 * Thrown with deliberate user-safe messages whenever the live avatar provider
 * is unavailable (disabled, keyless, unreachable, out of credits). Callers
 * catch it and fall back to static photo + voice — it never reaches chat.
 */
public class AvatarUnavailableException extends RuntimeException {

    public AvatarUnavailableException(String message) {
        super(message);
    }
}
