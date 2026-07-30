package com.santsg.tourvisio.guardrail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class ProfanityGuardrail {

    private static final Logger log = LoggerFactory.getLogger(ProfanityGuardrail.class);
    private static final Locale TR_LOCALE = Locale.forLanguageTag("tr-TR");
    private static final List<Pattern> PROFANITY_PATTERNS = new ArrayList<>();

    static {
        loadProfanityList();
    }

    private ProfanityGuardrail() {}

    private static void loadProfanityList() {
        try {
            ClassPathResource resource = new ClassPathResource("profanity_tr.txt");
            if (resource.exists()) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) {
                            continue;
                        }
                        String norm = normalizeText(line);
                        if (!norm.isEmpty()) {
                            // Word-boundary pattern to prevent false positives (Scunthorpe problem)
                            Pattern pattern = Pattern.compile("\\b" + Pattern.quote(norm) + "\\b",
                                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                            PROFANITY_PATTERNS.add(pattern);
                        }
                    }
                }
            } else {
                log.warn("[ProfanityGuardrail] profanity_tr.txt resource not found!");
            }
        } catch (Exception e) {
            log.error("[ProfanityGuardrail] Error loading profanity list", e);
        }
    }

    /**
     * Normalizes text for Turkish locale:
     * - Lowercase using tr-TR locale (preserving İ/i and I/ı properly)
     * - Character replacement (ş->s, ğ->g, ü->u, ö->o, ç->c, ı->i)
     */
    public static String normalizeText(String text) {
        if (text == null) return "";
        String lower = text.toLowerCase(TR_LOCALE);
        return lower
                .replace('ş', 's')
                .replace('ğ', 'g')
                .replace('ü', 'u')
                .replace('ö', 'o')
                .replace('ç', 'c')
                .replace('ı', 'i')
                .replace("i\u0307", "i");
    }

    /**
     * Checks if the message contains profanity or abusive terms.
     * Uses word boundaries to avoid Scunthorpe problems.
     * Also checks sanitized version (removing dots/spaces within obfuscated tokens like 'a.m.k' -> 'amk').
     */
    public static boolean isProfanity(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }

        String normalized = normalizeText(text);

        // 1. Direct word-boundary match on normalized text
        for (Pattern pattern : PROFANITY_PATTERNS) {
            if (pattern.matcher(normalized).find()) {
                log.info("[ProfanityGuardrail] Profanity detected via pattern: {}", pattern.pattern());
                return true;
            }
        }

        // 2. Obfuscation check: remove dots/dashes/spaces between characters (e.g. "a.m.k" -> "amk", "a m k" -> "amk")
        String stripped = normalized.replaceAll("[.,\\-_\\s]+", "");
        for (Pattern pattern : PROFANITY_PATTERNS) {
            if (pattern.matcher(stripped).find()) {
                log.info("[ProfanityGuardrail] Profanity detected via stripped pattern: {}", pattern.pattern());
                return true;
            }
        }

        return false;
    }
}
