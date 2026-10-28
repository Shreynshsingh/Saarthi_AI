package com.saarthi.ai.agent.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SafetyAgent is the crisis gate before Llama: it must catch self-harm intent
 * in English, Hinglish, and Hindi - and must never fire on normal stress talk.
 * Pure unit test, no Spring context, no database.
 */
class SafetyAgentTest {

    private final SafetyAgent safetyAgent = new SafetyAgent();

    @Test
    void crisisPhrasesTriggerAlert() {
        String[] phrases = {
                "I want to die",
                "I want to kill myself",
                "I don't want to live anymore",
                "mujhe marna chahta hoon",
                "suicide karna chahta hu",
                "मैं मरना चाहता हूँ",
                "I cut myself yesterday"
        };
        for (String phrase : phrases) {
            assertTrue(safetyAgent.isCrisis(phrase), "Should trigger crisis: " + phrase);
        }
    }

    @Test
    void normalStressTalkDoesNotTrigger() {
        String[] phrases = {
                "Main exams ko lekar bahut stressed hoon",
                "I am stressed about exams",
                "I feel anxious today",
                "This homework is killing me",
                "I'm fine"
        };
        for (String phrase : phrases) {
            assertFalse(safetyAgent.isCrisis(phrase), "Should NOT trigger crisis: " + phrase);
        }
    }

    @Test
    void nullAndBlankAreSafe() {
        assertFalse(safetyAgent.isCrisis(null));
        assertFalse(safetyAgent.isCrisis("   "));
    }

    @Test
    void helplineResponseContainsBothHelplines() {
        String response = safetyAgent.getHelplineResponse();

        assertTrue(response.contains("14416"), response);
        assertTrue(response.contains("Tele-MANAS"), response);
        assertTrue(response.contains("9820466726"), response);
        assertTrue(response.contains("AASRA"), response);
    }
}
