package com.saarthi.ai.dto;

/**
 * WebSocket message types. Single source of truth - exact strings, no typos.
 * Must match the frontend contract in 07_API_INTEGRATION.md.
 */
public final class WsMessageType {

    private WsMessageType() {
    }

    // Client -> Server
    public static final String USER_MESSAGE = "USER_MESSAGE";

    // Server -> Client
    public static final String MENTOR_RESPONSE_CHUNK = "MENTOR_RESPONSE_CHUNK";
    public static final String MENTOR_RESPONSE_END = "MENTOR_RESPONSE_END";
    public static final String TRANSCRIPTION = "TRANSCRIPTION";
    public static final String CRISIS_ALERT = "CRISIS_ALERT";
    public static final String ERROR = "ERROR";
}
