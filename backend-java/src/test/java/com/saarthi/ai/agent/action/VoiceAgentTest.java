package com.saarthi.ai.agent.action;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * TTS voice selection. Pure unit tests - no Python calls.
 */
class VoiceAgentTest {

    @Test
    void languageHintWins() {
        assertEquals("hi-IN-SwaraNeural", VoiceAgent.selectVoice("Hello there", "hi-IN"));
        assertEquals("hi-IN-SwaraNeural", VoiceAgent.selectVoice("Hello there", "HI-in"));
        assertEquals("en-IN-NeerjaNeural", VoiceAgent.selectVoice("Hello there", "en-IN"));
        assertEquals("en-IN-NeerjaNeural", VoiceAgent.selectVoice("Hello there", null));
    }

    @Test
    void devanagariSelectsHindiVoice() {
        assertEquals("hi-IN-SwaraNeural", VoiceAgent.selectVoice("नमस्ते दोस्त", null));
        assertEquals("hi-IN-SwaraNeural", VoiceAgent.selectVoice("Hello नमस्ते", "en-IN"));
    }

    @Test
    void hinglishKeywordsSelectHindiVoice() {
        assertEquals("hi-IN-SwaraNeural",
                VoiceAgent.selectVoice("Main exams ko lekar bahut stressed hoon", null));
        assertEquals("hi-IN-SwaraNeural",
                VoiceAgent.selectVoice("Tum kaise ho?", null));
    }

    @Test
    void plainEnglishKeepsDefaultVoice() {
        assertEquals("en-IN-NeerjaNeural", VoiceAgent.selectVoice("Hello there", null));
        assertEquals("en-IN-NeerjaNeural",
                VoiceAgent.selectVoice("The main reason is unclear", null));
        assertEquals("en-IN-NeerjaNeural", VoiceAgent.selectVoice("", null));
    }
}
