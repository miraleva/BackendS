package com.santsg.tourvisio.service;

import com.santsg.tourvisio.client.AIProviderClient;
import org.springframework.stereotype.Service;
import java.util.Locale;

@Service
public class IntentDetectionService {

    private final AIProviderClient aiProviderClient;

    public IntentDetectionService(AIProviderClient aiProviderClient) {
        this.aiProviderClient = aiProviderClient;
    }

    public String detectIntent(String userMessage) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return "UNKNOWN";
        }

        // AI ile intent tespiti (API key tanımlıysa)
        try {
            String prompt = """
                    Kullanıcının seyahat asistanına gönderdiği şu mesajın amacını (intent) tespit et.
                    Sadece aşağıdaki dört değerden birini dön (başka hiçbir metin dönme): //TO DO:aşağıdakilerle eşleşenleri döndürsün değiştir.
                    - HOTEL_SEARCH: Otel aramak, oda bakmak, konaklamak istiyorsa.  
                    - FLIGHT_SEARCH: Uçak bileti, uçuş aramak istiyorsa.
                    - OUT_OF_SCOPE: Seyahat konusuyla alakasız bir sohbet ise.
                    - UNKNOWN: Selamlaşma veya ne aradığını belirtmediği belirsiz durumlar için.
                    Kullanıcı mesajı: "%s"
                    Değer:""".formatted(userMessage);
                     // kullanıcının mesaj örenkleri dört değer içinde ekle.

            String response = aiProviderClient.complete(prompt);
            if (response != null && !response.trim().startsWith("[MOCK]")) {
                String cleanResponse = response.trim().toUpperCase();
                if (cleanResponse.contains("HOTEL_SEARCH")) return "HOTEL_SEARCH";
                if (cleanResponse.contains("FLIGHT_SEARCH")) return "FLIGHT_SEARCH";
                if (cleanResponse.contains("OUT_OF_SCOPE")) return "OUT_OF_SCOPE";
                if (cleanResponse.contains("UNKNOWN")) return "UNKNOWN";
            }
        } catch (Exception e) {
            // Hata durumunda veya key yoksa rule-based yönteme devam et
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
