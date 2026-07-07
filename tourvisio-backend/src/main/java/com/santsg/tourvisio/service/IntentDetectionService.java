package com.santsg.tourvisio.service;

import org.springframework.stereotype.Service;
import java.util.Locale;

@Service
public class IntentDetectionService {

    public String detectIntent(String userMessage) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return "UNKNOWN";
        }

        // Convert to lowercase using Turkish locale to handle characters like I/ı and
        // İ/i correctly
        String lowerMsg = userMessage.toLowerCase(Locale.forLanguageTag("tr-TR"));

        // Keywords for Hotel Search
        boolean hasHotelKeywords = lowerMsg.contains("otel") ||
                lowerMsg.contains("hotel") ||
                lowerMsg.contains("konaklama") ||
                lowerMsg.contains("kalacak") ||
                lowerMsg.contains("pansiyon") ||
                lowerMsg.contains("apart") ||
                lowerMsg.contains("oda");

        // Keywords for Flight Search
        boolean hasFlightKeywords = lowerMsg.contains("uçak") ||
                lowerMsg.contains("ucak") ||
                lowerMsg.contains("uçuş") ||
                lowerMsg.contains("ucus") ||
                lowerMsg.contains("sefer") ||
                lowerMsg.contains("fly") ||
                lowerMsg.contains("havaliman");

        // Keywords for Reservations
        boolean hasReservationKeywords = lowerMsg.contains("rezervasyon") ||
                lowerMsg.contains("rezerv") ||
                lowerMsg.contains("rezerve") ||
                lowerMsg.contains("booking") ||
                lowerMsg.contains("ayırt") ||
                lowerMsg.contains("ayirt") ||
                lowerMsg.contains("bilet");

        // Keywords for Greetings / General Conversations (mapped to UNKNOWN)
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
                lowerMsg.contains("hi");

        if (hasHotelKeywords && !hasFlightKeywords) {
            return "HOTEL_SEARCH";
        } else if (hasFlightKeywords && !hasHotelKeywords) {
            return "FLIGHT_SEARCH";
        } else if (hasReservationKeywords || hasGreetingKeywords) {
            return "UNKNOWN";
        }

        // If it's none of the above, it's completely out of scope
        return "OUT_OF_SCOPE";
    }
}
