package com.santsg.tourvisio.service;

import org.springframework.stereotype.Service;
import java.util.Locale;

@Service
public class IntentDetectionService {

    public String detectIntent(String userMessage) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return "UNKNOWN";
        }

        // Convert to lowercase using Turkish locale to handle characters like I/ı and İ/i correctly
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
                                    lowerMsg.contains("bilet") ||
                                    lowerMsg.contains("sefer") ||
                                    lowerMsg.contains("fly") ||
                                    lowerMsg.contains("havaliman");

        if (hasHotelKeywords && !hasFlightKeywords) {
            return "HOTEL_SEARCH";
        } else if (hasFlightKeywords && !hasHotelKeywords) {
            return "FLIGHT_SEARCH";
        }

        return "UNKNOWN";
    }
}
