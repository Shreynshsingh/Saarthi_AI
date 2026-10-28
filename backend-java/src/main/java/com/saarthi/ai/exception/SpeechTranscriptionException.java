package com.saarthi.ai.exception;

/**
 * Whisper transcription failed (timeout, API error). Thrown by SpeechAgent,
 * caught by the orchestrator and converted to a calm client ERROR.
 * Never carries provider internals outward.
 */
public class SpeechTranscriptionException extends RuntimeException {

    public SpeechTranscriptionException(String message) {
        super(message);
    }

    public SpeechTranscriptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
