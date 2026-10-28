package com.saarthi.ai.agent.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Safety gate. Runs BEFORE Llama on every message (see 08_AGENT_CONNECTION.md).
 * Pure keyword + phrase matching - fast (&lt;1ms), deterministic, no API call.
 * Deliberately tuned for high recall on self-harm intent; normal stress talk
 * ("I am stressed about exams") must never match.
 */
@Slf4j
@Service
public class SafetyAgent {

    public static final String CRISIS_MESSAGE =
            "I'm really glad you told me this. Please don't go through this alone. "
            + "You can call Tele-MANAS at 14416 (24x7) or AASRA at 9820466726 right now, "
            + "and please reach out to a person you trust near you. I'm here to listen.";

    public static final List<String> HELPLINES = List.of("14416", "9820466726", "9152987821");

    private static final List<String> CRISIS_PHRASES = List.of(
            // English - explicit self-harm / suicide intent
            "kill myself", "kill my self", "killing myself",
            "end my life", "take my life", "take my own life",
            "want to die", "wanna die", "want to kill myself",
            "don't want to live", "dont want to live", "do not want to live",
            "no reason to live", "better off dead", "end it all",
            "hurt myself", "harm myself", "cut myself", "cutting myself",
            "hang myself", "overdose", "overdosed", "jump off",
            "suicide", "suicidal", "self-harm", "selfharm", "self harm",
            // Hinglish (Latin script Hindi)
            "marna chahta", "marna chahti", "marna hai", "marna chahiye",
            "mar jau", "mar jaau", "mar jaaun", "mar jaunga", "mar jaungi",
            "suicide kar", "suicide karna", "khudkushi",
            "khud ko maar", "apni jaan", "jaan dena", "jaan de",
            // Hindi (Devanagari)
            "मरना", "आत्महत्या", "खुदकुशी", "जान दे");

    // Whole-word/phrase match so "suicide" never matches inside other words,
    // and "stressed about exams" style messages never trigger.
    private static final List<Pattern> CRISIS_PATTERNS = CRISIS_PHRASES.stream()
            .map(phrase -> Pattern.compile(
                    "(?<![\\p{L}\\p{N}_])" + Pattern.quote(phrase) + "(?![\\p{L}\\p{N}_])"))
            .toList();

    public boolean isCrisis(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        for (Pattern pattern : CRISIS_PATTERNS) {
            if (pattern.matcher(normalized).find()) {
                // Log the fact, never the user's words.
                log.info("[SafetyAgent] Crisis pattern matched, bypassing Llama");
                return true;
            }
        }
        return false;
    }

    /**
     * Fixed crisis reply. Always the same text so a vulnerable user never gets
     * a generated, possibly inappropriate response.
     */
    public String getHelplineResponse() {
        return CRISIS_MESSAGE;
    }
}
