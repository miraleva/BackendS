package com.santsg.tourvisio.guardrail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProfanityGuardrailTest {

    @Test
    @DisplayName("Detects exact Turkish profanity terms")
    void testProfanityDetectionExact() {
        assertTrue(ProfanityGuardrail.isProfanity("amk"));
        assertTrue(ProfanityGuardrail.isProfanity("orospu"));
        assertTrue(ProfanityGuardrail.isProfanity("şerefsiz"));
        assertTrue(ProfanityGuardrail.isProfanity("serefsiz"));
    }

    @Test
    @DisplayName("Detects obfuscated profanity with dots or spaces")
    void testProfanityDetectionObfuscated() {
        assertTrue(ProfanityGuardrail.isProfanity("a.m.k"));
        assertTrue(ProfanityGuardrail.isProfanity("a m k"));
    }

    @Test
    @DisplayName("Does NOT trigger false positives on innocent substring matches (Scunthorpe problem)")
    void testInnocentWordsNotFlagged() {
        assertFalse(ProfanityGuardrail.isProfanity("Beşiktaş'ta otel arıyorum"));
        assertFalse(ProfanityGuardrail.isProfanity("Fethiye'de otel rezervasyonu"));
        assertFalse(ProfanityGuardrail.isProfanity("Otogara yakın otel var mı?"));
        assertFalse(ProfanityGuardrail.isProfanity("İstanbul'dan Antalya'ya uçak bileti"));
    }

    @Test
    @DisplayName("Handles Turkish locale lowercasing correctly")
    void testTurkishLocaleLowercasing() {
        assertTrue(ProfanityGuardrail.isProfanity("ŞEREFSİZ"));
        assertTrue(ProfanityGuardrail.isProfanity("AMK"));
    }
}
