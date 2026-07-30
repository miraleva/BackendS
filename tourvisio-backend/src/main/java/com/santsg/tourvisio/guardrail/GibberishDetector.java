package com.santsg.tourvisio.guardrail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.regex.Pattern;

public final class GibberishDetector {

    private static final Logger log = LoggerFactory.getLogger(GibberishDetector.class);
    private static final Locale TR_LOCALE = Locale.forLanguageTag("tr-TR");

    // Repeated punctuation: 3 or more ?, !, or .
    private static final Pattern REPEATED_PUNCTUATION = Pattern.compile("([?!.]){3,}");

    // Character spam: 4 or more identical consecutive characters (e.g. "aaaaa", "zzzz")
    private static final Pattern CHARACTER_SPAM = Pattern.compile("(.)\\1{3,}");

    // Consonant spam: 6 or more consecutive consonants without a vowel
    private static final Pattern CONSONANT_SPAM = Pattern.compile("[bcdfghjklmnprstvwxyzBCDFGHJKLMNPRSTVWXYZ]{6,}");

    private GibberishDetector() {}

    /**
     * Lightly evaluates if a message is obvious gibberish or character spam.
     * Kept cautious so valid city names (e.g. Zurich, Reykjavik, Gdansk) are NOT rejected.
     */
    public static boolean isGibberish(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }

        String trimmed = text.trim();

        // 1. Repeated punctuation check (e.g., "???????", "!!!!!!!", "........")
        if (REPEATED_PUNCTUATION.matcher(trimmed).find()) {
            log.info("[GibberishDetector] Gibberish detected: repeated punctuation in '{}'", trimmed);
            return true;
        }

        // 2. Character spam check (e.g., "zzzzxx", "aaaaaaa")
        if (CHARACTER_SPAM.matcher(trimmed).find()) {
            log.info("[GibberishDetector] Gibberish detected: character spam in '{}'", trimmed);
            return true;
        }

        // 3. Consonant spam check (e.g., "asdljk", "qwrtpzs")
        String normalized = ProfanityGuardrail.normalizeText(trimmed);
        if (CONSONANT_SPAM.matcher(normalized).find()) {
            log.info("[GibberishDetector] Gibberish detected: consonant spam in '{}'", trimmed);
            return true;
        }

        return false;
    }
}
