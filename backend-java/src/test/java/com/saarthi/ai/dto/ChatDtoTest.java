package com.saarthi.ai.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the frozen WebSocket wire contract: exact type strings, exact fields.
 * No Spring context needed.
 */
class ChatDtoTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void chunkSerializesWithExactContract() throws Exception {
        UUID conversationId = UUID.randomUUID();
        ChatResponse chunk = new ChatResponse(
                WsMessageType.MENTOR_RESPONSE_CHUNK, "Hello ", null, "QUJDRA==", conversationId, false, true);

        String json = objectMapper.writeValueAsString(chunk);

        assertTrue(json.contains("\"type\":\"MENTOR_RESPONSE_CHUNK\""), json);
        assertTrue(json.contains("\"textChunk\":\"Hello \""), json);
        assertTrue(json.contains("\"conversationId\":\"" + conversationId + "\""), json);
        assertTrue(json.contains("\"isFinal\":false"), json);
        assertTrue(json.contains("\"audioBase64\":\"QUJDRA==\""), json);
        assertTrue(json.contains("\"avatarSpeaking\":true"), json);
    }

    @Test
    void chatRequestDeserializes() throws Exception {
        String json = "{\"type\":\"USER_MESSAGE\",\"conversationId\":null,"
                + "\"inputMode\":\"VOICE\",\"text\":\"\",\"audioBase64\":\"aGVsbG8=\","
                + "\"emotion\":null,\"emotionConfidence\":null,\"signDetected\":\"HELLO\"}";

        ChatRequest request = objectMapper.readValue(json, ChatRequest.class);

        assertEquals(WsMessageType.USER_MESSAGE, request.type());
        assertEquals("VOICE", request.inputMode());
        assertEquals("aGVsbG8=", request.audioBase64());
        assertEquals("HELLO", request.signDetected());
    }

    @Test
    void transcriptionSerializesWithExactContract() throws Exception {
        UUID conversationId = UUID.randomUUID();
        ChatResponse transcription = new ChatResponse(
                WsMessageType.TRANSCRIPTION, null, "transcribed text", null, conversationId, false, false);

        String json = objectMapper.writeValueAsString(transcription);

        assertTrue(json.contains("\"type\":\"TRANSCRIPTION\""), json);
        assertTrue(json.contains("\"transcribedText\":\"transcribed text\""), json);
        assertTrue(json.contains("\"conversationId\":\"" + conversationId + "\""), json);
        assertTrue(json.contains("\"isFinal\":false"), json);
    }

    @Test
    void crisisAlertCarriesMessageText() throws Exception {
        UUID conversationId = UUID.randomUUID();
        ChatResponse crisis = new ChatResponse(
                WsMessageType.CRISIS_ALERT, "Please call Tele-MANAS at 14416", null, null, conversationId, true, false);

        String json = objectMapper.writeValueAsString(crisis);

        assertTrue(json.contains("\"type\":\"CRISIS_ALERT\""), json);
        assertTrue(json.contains("14416"), json);
        assertTrue(json.contains("\"isFinal\":true"), json);
    }

    @Test
    void endCarriesConversationId() throws Exception {
        UUID conversationId = UUID.randomUUID();
        ChatResponse end = new ChatResponse(
                WsMessageType.MENTOR_RESPONSE_END, null, null, null, conversationId, true, false);

        String json = objectMapper.writeValueAsString(end);

        assertTrue(json.contains("\"type\":\"MENTOR_RESPONSE_END\""), json);
        assertTrue(json.contains("\"conversationId\":\"" + conversationId + "\""), json);
        assertTrue(json.contains("\"isFinal\":true"), json);
    }

    @Test
    void messageTypesMatchContract() {
        assertEquals("USER_MESSAGE", WsMessageType.USER_MESSAGE);
        assertEquals("MENTOR_RESPONSE_CHUNK", WsMessageType.MENTOR_RESPONSE_CHUNK);
        assertEquals("MENTOR_RESPONSE_END", WsMessageType.MENTOR_RESPONSE_END);
        assertEquals("TRANSCRIPTION", WsMessageType.TRANSCRIPTION);
        assertEquals("CRISIS_ALERT", WsMessageType.CRISIS_ALERT);
        assertEquals("ERROR", WsMessageType.ERROR);
    }

    @Test
    void chatRequestDeserializesLandmarks() throws Exception {
        StringBuilder points = new StringBuilder();
        for (int i = 0; i < 21; i++) {
            if (i > 0) {
                points.append(",");
            }
            points.append("[0.5,0.4,0.01]");
        }
        String json = "{\"type\":\"USER_MESSAGE\",\"inputMode\":\"SIGN\","
                + "\"landmarks\":[" + points + "]}";

        ChatRequest request = objectMapper.readValue(json, ChatRequest.class);

        assertEquals("SIGN", request.inputMode());
        assertEquals(21, request.landmarks().size());
        assertEquals(3, request.landmarks().get(0).size());
        assertEquals(0.5, request.landmarks().get(0).get(0));
    }
}
