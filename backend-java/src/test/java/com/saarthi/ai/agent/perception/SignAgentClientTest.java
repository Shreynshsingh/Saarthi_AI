package com.saarthi.ai.agent.perception;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sign-client guards and response parsing. Pure unit tests - no Python calls.
 */
class SignAgentClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static List<List<Double>> frame() {
        List<List<Double>> landmarks = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            landmarks.add(new ArrayList<>(List.of(0.5, 0.5, 0.0)));
        }
        return landmarks;
    }

    @Test
    void disabledClientNeverCallsPython() {
        // Null WebClient: a disabled client must return before any HTTP call.
        SignAgentClient client = new SignAgentClient(null, objectMapper, false, 0.80, 2000L);

        SignAgentClient.SignResult result = client.predictSign(frame()).block();

        assertEquals(SignAgentClient.EMPTY, result);
    }

    @Test
    void malformedFramesReturnEmptyWithoutNetwork() {
        // Null WebClient: validation must return before any HTTP call.
        SignAgentClient client = new SignAgentClient(null, objectMapper, true, 0.80, 2000L);

        assertEquals(SignAgentClient.EMPTY, client.predictSign(null).block());
        assertEquals(SignAgentClient.EMPTY, client.predictSign(List.of()).block());
        assertEquals(SignAgentClient.EMPTY,
                client.predictSign(frame().subList(0, 20)).block());

        List<List<Double>> ragged = frame();
        ragged.set(0, List.of(0.5, 0.5));
        assertEquals(SignAgentClient.EMPTY, client.predictSign(ragged).block());

        List<List<Double>> nan = frame();
        nan.set(5, new ArrayList<>(List.of(Double.NaN, 0.5, 0.0)));
        assertEquals(SignAgentClient.EMPTY, client.predictSign(nan).block());
    }

    @Test
    void frameValidationRequires21FiniteTriples() {
        assertTrue(SignAgentClient.isValidFrame(frame()));
        assertFalse(SignAgentClient.isValidFrame(null));
        assertFalse(SignAgentClient.isValidFrame(Collections.emptyList()));

        List<List<Double>> infinite = frame();
        infinite.set(0, new ArrayList<>(List.of(Double.POSITIVE_INFINITY, 0.0, 0.0)));
        assertFalse(SignAgentClient.isValidFrame(infinite));
    }

    @Test
    void parseAcceptsConfidentLabels() {
        SignAgentClient.SignResult result = SignAgentClient.parseSignResult(
                objectMapper, "{\"sign\":\"help\",\"confidence\":0.94}", 0.80);

        assertEquals("HELP", result.sign());
        assertEquals(0.94, result.confidence());
    }

    @Test
    void parseRejectsThresholdAndBelow() {
        // Contract with Python: 0.80 is the highest "near match" score, and the
        // threshold is strictly-greater, so near matches never become messages.
        assertEquals(SignAgentClient.EMPTY, SignAgentClient.parseSignResult(
                objectMapper, "{\"sign\":\"HELP\",\"confidence\":0.80}", 0.80));
        assertEquals(SignAgentClient.EMPTY, SignAgentClient.parseSignResult(
                objectMapper, "{\"sign\":\"HELP\",\"confidence\":0.70}", 0.80));
    }

    @Test
    void parseRejectsNullBlankAndBrokenBodies() {
        assertEquals(SignAgentClient.EMPTY, SignAgentClient.parseSignResult(
                objectMapper, "{\"sign\":null,\"confidence\":0.0}", 0.80));
        assertEquals(SignAgentClient.EMPTY, SignAgentClient.parseSignResult(
                objectMapper, "{\"sign\":\"  \",\"confidence\":1.0}", 0.80));
        assertEquals(SignAgentClient.EMPTY, SignAgentClient.parseSignResult(
                objectMapper, "{}", 0.80));
        assertEquals(SignAgentClient.EMPTY, SignAgentClient.parseSignResult(
                objectMapper, "not-json", 0.80));
        assertEquals(SignAgentClient.EMPTY,
                SignAgentClient.parseSignResult(objectMapper, null, 0.80));

        SignAgentClient.SignResult missingConfidence = SignAgentClient.parseSignResult(
                objectMapper, "{\"sign\":\"HELP\"}", 0.80);
        assertNull(missingConfidence.sign());
    }
}
