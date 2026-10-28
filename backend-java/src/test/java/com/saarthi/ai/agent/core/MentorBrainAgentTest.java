package com.saarthi.ai.agent.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * System-prompt placeholder resolution. Pure string logic, no Spring, no API calls.
 */
class MentorBrainAgentTest {

    private static final String TEMPLATE = "Mentoring {{user_name}} | {{emotion}} | {{preferred_language}}";

    @Test
    void resolvesAllPlaceholders() {
        String resolved = MentorBrainAgent.resolvePlaceholders(
                TEMPLATE, "Aarav", "stressed", "Hinglish");

        assertEquals("Mentoring Aarav | STRESSED | Hinglish", resolved);
    }

    @Test
    void nullsFallBackToDefaults() {
        String resolved = MentorBrainAgent.resolvePlaceholders(TEMPLATE, null, null, null);

        assertEquals("Mentoring friend | NEUTRAL | English", resolved);
    }

    @Test
    void blanksFallBackToDefaults() {
        String resolved = MentorBrainAgent.resolvePlaceholders(TEMPLATE, "   ", "", "  ");

        assertEquals("Mentoring friend | NEUTRAL | English", resolved);
    }
}
