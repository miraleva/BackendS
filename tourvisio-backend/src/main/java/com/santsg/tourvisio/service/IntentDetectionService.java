package com.santsg.tourvisio.service;

import com.santsg.tourvisio.guardrail.GibberishDetector;
import com.santsg.tourvisio.guardrail.ProfanityGuardrail;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class IntentDetectionService {

    private static final List<String> UNSUPPORTED_SERVICE_KEYWORDS = List.of(
            "otobüs", "otobus", "bus", "tren", "train", "araba kiralama", "araç kiralama", "car rental",
            "feribot", "gemi", "ferry", "vize", "visa", "sigorta", "insurance", "transfer",
            "tur paketi", "tur", "tour", "hava durumu", "weather", "maç", "futbol", "restaurant", "restoran"
    );

    public IntentDetectionService() {
    }

    public String detectIntent(String userMessage) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return "UNKNOWN";
        }

        // Priority 1: Profanity Check
        if (ProfanityGuardrail.isProfanity(userMessage)) {
            return "PROFANITY";
        }

        String lowerMsg = userMessage.toLowerCase(Locale.forLanguageTag("tr-TR"));

        // Priority 2: Valid Search Keywords
        boolean hasHotelKeywords = lowerMsg.contains("otel") ||
                lowerMsg.contains("hotel") ||
                lowerMsg.contains("konaklama") ||
                lowerMsg.contains("kalacak") ||
                lowerMsg.contains("pansiyon") ||
                lowerMsg.contains("apart") ||
                lowerMsg.contains("oda");

        boolean hasFlightKeywords = lowerMsg.contains("uçak") ||
                lowerMsg.contains("ucak") ||
                lowerMsg.contains("uçuş") ||
                lowerMsg.contains("ucus") ||
                lowerMsg.contains("sefer") ||
                lowerMsg.contains("fly") ||
                lowerMsg.contains("flight") ||
                lowerMsg.contains("plane") ||
                lowerMsg.contains("airline") ||
                lowerMsg.contains("airport") ||
                lowerMsg.contains("havaliman");

        if (hasHotelKeywords && !hasFlightKeywords) {
            return "HOTEL_SEARCH";
        } else if (hasFlightKeywords && !hasHotelKeywords) {
            return "FLIGHT_SEARCH";
        }

        // Priority 3: Unsupported Service Scope Check (checked before generic "bilet" reservation keywords)
        for (String keyword : UNSUPPORTED_SERVICE_KEYWORDS) {
            if (lowerMsg.contains(keyword)) {
                return "OUT_OF_SCOPE";
            }
        }

        // Priority 4: Normal Conversation / Greeting / Reservation Follow-up (mapped to UNKNOWN)
        boolean hasReservationKeywords = lowerMsg.contains("rezervasyon") ||
                lowerMsg.contains("rezerv") ||
                lowerMsg.contains("rezerve") ||
                lowerMsg.contains("booking") ||
                lowerMsg.contains("ayırt") ||
                lowerMsg.contains("ayirt") ||
                lowerMsg.contains("bilet");

        boolean hasGreetingKeywords = lowerMsg.contains("merhaba") ||
                lowerMsg.contains("selam") ||
                lowerMsg.contains("hey") ||
                lowerMsg.contains("günaydın") ||
                lowerMsg.contains("gunaydin") ||
                lowerMsg.contains("tünaydın") ||
                lowerMsg.contains("iyi günler") ||
                lowerMsg.contains("slm") ||
                lowerMsg.contains("mrb") ||
                lowerMsg.contains("hello") ||
                lowerMsg.contains("hi") ||
                lowerMsg.contains("teşekkür") ||
                lowerMsg.contains("tesekkur") ||
                lowerMsg.contains("thanks") ||
                lowerMsg.contains("sağol") ||
                lowerMsg.contains("sagol") ||
                lowerMsg.contains("tamam") ||
                lowerMsg.contains("ok");

        if (hasReservationKeywords || hasGreetingKeywords) {
            return "UNKNOWN";
        }

        // Priority 5: Irrelevant / Gibberish Detection
        if (GibberishDetector.isGibberish(userMessage)) {
            return "IRRELEVANT";
        }

        // Default out-of-scope for unmatched non-gibberish statements
        return "OUT_OF_SCOPE";
    }
}
