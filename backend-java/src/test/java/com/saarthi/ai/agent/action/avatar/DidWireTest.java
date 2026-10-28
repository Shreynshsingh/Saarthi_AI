package com.saarthi.ai.agent.action.avatar;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the D-ID wire contract (snake_case field names). No network.
 */
class DidWireTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void talkRequestUsesProviderFieldNames() throws Exception {
        DidAvatarServiceImpl.TalkRequest talk = new DidAvatarServiceImpl.TalkRequest(
                new DidAvatarServiceImpl.AudioScript("https://cdn.example/t1.mp3"),
                Map.of("stitch", true), "sess-1");

        String json = objectMapper.writeValueAsString(talk);

        assertTrue(json.contains("\"audio_url\":\"https://cdn.example/t1.mp3\""), json);
        assertTrue(json.contains("\"type\":\"audio\""), json);
        assertTrue(json.contains("\"session_id\":\"sess-1\""), json);
    }

    @Test
    void streamCreatedDeserializes() throws Exception {
        String json = "{\"id\":\"str_1\",\"session_id\":\"sess-1\","
                + "\"offer\":{\"type\":\"offer\",\"sdp\":\"v=0\"},"
                + "\"ice_servers\":[{\"urls\":\"stun:stun.example\"}],"
                + "\"status\":\"created\"}";

        DidAvatarServiceImpl.StreamCreated created =
                objectMapper.readValue(json, DidAvatarServiceImpl.StreamCreated.class);

        assertEquals("str_1", created.id());
        assertEquals("sess-1", created.sessionId());
        assertEquals("v=0", created.offer().sdp());
        assertEquals(List.of(Map.of("urls", "stun:stun.example")), created.iceServers());
    }

    @Test
    void answerAndIceSerialize() throws Exception {
        DidAvatarServiceImpl.SdpAnswer answer = new DidAvatarServiceImpl.SdpAnswer(
                new DidAvatarServiceImpl.Sdp("answer", "v=0-answer"), "sess-1");

        String answerJson = objectMapper.writeValueAsString(answer);

        assertTrue(answerJson.contains("\"session_id\":\"sess-1\""), answerJson);
        assertTrue(answerJson.contains("\"sdp\":\"v=0-answer\""), answerJson);
        assertTrue(answerJson.contains("\"answer\":{\"type\":\"answer\""), answerJson);
    }
}
