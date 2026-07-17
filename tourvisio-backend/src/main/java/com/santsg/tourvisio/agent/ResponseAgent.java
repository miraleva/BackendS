package com.santsg.tourvisio.agent;

import com.santsg.tourvisio.chat.SearchCriteria;
import com.santsg.tourvisio.client.GeminiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Component
public class ResponseAgent {

    private static final Logger log = LoggerFactory.getLogger(ResponseAgent.class);

    private final GeminiClient geminiClient;
    private final MessageSource messageSource;

    public ResponseAgent(GeminiClient geminiClient, MessageSource messageSource) {
        this.geminiClient = geminiClient;
        this.messageSource = messageSource;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public Scenarios
    // ─────────────────────────────────────────────────────────────────────────

    public String decline(SearchCriteria criteria, boolean isTerminated) {
        Locale locale = resolveLocale(criteria);
        String targetLanguage = resolveLanguageName(criteria);
        String targetCountry = (criteria != null && criteria.getCountry() != null) ? criteria.getCountry() : "United Kingdom";

        String prompt = String.format(
                "Write a polite travel assistant response declining the user's out-of-scope travel query. " +
                "Explain politely that we can only assist with hotel search, flight search, and booking/reservations. " +
                "Write the response in the official language of %s (%s). " +
                "Context status: %s. " +
                "Return ONLY the response itself, no extra notes or introductions.",
                targetCountry, targetLanguage, isTerminated ? "TERMINATED" : "ACTIVE"
        );

        try {
            String aiResponse = geminiClient.generate(prompt);
            if (isValidResponse(aiResponse)) {
                return aiResponse.trim();
            }
        } catch (Exception e) {
            log.warn("[ResponseAgent] Decline AI generation failed, using fallback localization: {}", e.getMessage());
        }

        String key = isTerminated ? "out.of.scope.terminated" : "out.of.scope";
        return messageSource.getMessage(key, null, locale);
    }

    private Locale detectFallbackLocale(String message) {
        if (message == null || message.trim().isEmpty()) {
            return Locale.ENGLISH;
        }
        String lower = message.trim().toLowerCase(Locale.ROOT);
        
        // Turkish
        if (lower.contains("merhaba") || lower.contains("selam") || lower.contains("nasılsın")
                || lower.contains("ç") || lower.contains("ş") || lower.contains("ğ") || lower.contains("ı")
                || lower.contains("ü") || lower.contains("ö")) { // Tr also has ü and ö but usually handled together, wait, let's keep it simple
            return Locale.forLanguageTag("tr-TR");
        }
        // German
        if (lower.contains("hallo") || lower.contains("guten") || lower.contains("morgen")
                || lower.contains("ß") || lower.contains("ä")) {
            return Locale.GERMAN;
        }
        // Russian
        if (lower.contains("привет") || lower.contains("здравствуйте") || lower.matches(".*[а-яА-Я].*")) {
            return Locale.forLanguageTag("ru-RU");
        }
        
        return Locale.ENGLISH;
    }

    public String welcome(String userMessage) {
        Locale locale = detectFallbackLocale(userMessage);
        String prompt = String.format(
                "The user just started a chat and sent their first message: \"%s\".\n" +
                "Write a warm, welcoming onboarding message as a travel assistant. " +
                "Briefly explain that you need their destination, dates, and guest count to find the best hotel or flight options. " +
                "Write the response in the language of this user message.\n" +
                "Return ONLY the response itself, no extra notes or greetings.",
                userMessage != null ? userMessage.replace("\"", "\\\"") : ""
        );

        try {
            String aiResponse = geminiClient.generate(prompt);
            if (isValidResponse(aiResponse)) {
                return aiResponse.trim();
            }
        } catch (Exception e) {
            log.warn("[ResponseAgent] welcome AI generation failed", e);
        }

        return messageSource.getMessage("welcome.intent", null, locale);
    }

    public String clarify(SearchCriteria criteria) {
        Locale locale = resolveLocale(criteria);
        String targetLanguage = resolveLanguageName(criteria);
        String targetCountry = (criteria != null && criteria.getCountry() != null) ? criteria.getCountry() : "United Kingdom";

        String prompt = String.format(
                "Write a polite travel assistant question asking the user whether they would like to search for a hotel or a flight ticket. " +
                "Write the response in the official language of %s (%s). " +
                "Return ONLY the question itself, no extra notes.",
                targetCountry, targetLanguage
        );

        try {
            String aiResponse = geminiClient.generate(prompt);
            if (isValidResponse(aiResponse)) {
                return aiResponse.trim();
            }
        } catch (Exception e) {
            log.warn("[ResponseAgent] Clarify AI generation failed, using fallback localization: {}", e.getMessage());
        }

        return messageSource.getMessage("clarify.intent", null, locale);
    }

    public String askMissing(List<String> missingFields, SearchCriteria criteria) {
        Locale locale = resolveLocale(criteria);
        String targetLanguage = resolveLanguageName(criteria);
        String targetCountry = (criteria != null && criteria.getCountry() != null) ? criteria.getCountry() : "United Kingdom";

        String fieldsCsv = String.join(", ", missingFields);
        String prompt = String.format(
                "The user is planning a trip, but the following mandatory search criteria are missing: [%s]. " +
                "Ask the user for ALL of this information together in a single, friendly, and natural question. " +
                "Do NOT use bare technical terms (e.g., say 'How many people will be traveling?' instead of 'adult count'). " +
                "Write the question in the official language of %s (%s). " +
                "Return ONLY the question itself, no extra notes.",
                fieldsCsv, targetCountry, targetLanguage
        );

        try {
            String aiResponse = geminiClient.generate(prompt);
            if (isValidResponse(aiResponse)) {
                return aiResponse.trim();
            }
        } catch (Exception e) {
            log.warn("[ResponseAgent] AskMissing AI generation failed, using fallback localization: {}", e.getMessage());
        }

        // Fallback localization pathway
        List<String> translatedFields = missingFields.stream()
                .map(field -> {
                    String fieldKey = getFieldKey(field);
                    if (fieldKey != null) {
                        return messageSource.getMessage(fieldKey, null, locale);
                    }
                    return field;
                })
                .collect(Collectors.toList());

        if (translatedFields.size() == 1) {
            return messageSource.getMessage("ask.missing.single", new Object[]{translatedFields.get(0)}, locale);
        } else {
            String joinedFields = String.join(", ", translatedFields);
            return messageSource.getMessage("ask.missing.multiple", new Object[]{joinedFields}, locale);
        }
    }

    public String summarize(String intent, String resultsJson, String defaultReply, SearchCriteria criteria, String userMessage, int totalResults, int shownResults) {
        Locale locale = resolveLocale(criteria);
        String targetLanguage = resolveLanguageName(criteria);
        String targetCountry = (criteria != null && criteria.getCountry() != null) ? criteria.getCountry() : "United Kingdom";

        String childNote = "";
        if (criteria != null && criteria.getChildCount() != null && criteria.getChildCount() > 0) {
            childNote = "\nNote: Some hotels may have varying age limits for child discounts (often up to 12). We can verify the exact policy for your chosen hotel.";
        }

        String countNote = "";
        if (totalResults > shownResults) {
            countNote = String.format("\nFound %d matches for your criteria. Here are the top %d best options:", totalResults, shownResults);
        } else {
            countNote = String.format("\nFound %d matches for your criteria. Here they are:", totalResults);
        }

        String prompt = String.format(
                "The user's travel search has been completed successfully. Here are the search results in JSON format:\n" +
                "Search Type: %s\n" +
                "Results:\n%s\n\n" +
                "Write a warm, polite, and engaging assistant response summarizing these results. " +
                "Do NOT use terse lists like 'En iyi teklif: X'. Instead, write 1-2 natural sentences per top recommendation. " +
                "Include the following context naturally in your response:\n%s%s\n\n" +
                "IMPORTANT RULES:\n" +
                "1. Write the response in the official language of %s (%s).\n" +
                "2. Only mention facts from the provided JSON results.\n" +
                "3. Never invent nicer names for raw system/sandbox data (e.g. if the room name is 'low level yerel dil' or 'BUILD131', present it exactly as is without fabricating a nicer name).\n" +
                "4. Return ONLY the assistant's summary response, with no notes or extra text.",
                intent, resultsJson, countNote, childNote, targetCountry, targetLanguage
        );

        try {
            String aiResponse = geminiClient.generate(prompt);
            if (isValidResponse(aiResponse)) {
                return aiResponse.trim();
            }
        } catch (Exception e) {
            log.warn("[ResponseAgent] Summarize AI generation failed, using fallback localization: {}", e.getMessage(), e);
        }

        // Fallback summary response if AI is down: return defaultReply
        if (defaultReply != null && !defaultReply.isBlank()) {
            return defaultReply;
        }
        return messageSource.getMessage("search.success.fallback", null, locale);
    }

    public String confirmSelection(Object selectedItem, SearchCriteria criteria) {
        Locale locale = resolveLocale(criteria);
        String itemName = "";
        if (selectedItem instanceof com.santsg.tourvisio.dto.HotelSearchResponseItem) {
            itemName = ((com.santsg.tourvisio.dto.HotelSearchResponseItem) selectedItem).getName();
        } else if (selectedItem instanceof com.santsg.tourvisio.dto.FlightSearchResponseItem) {
            itemName = ((com.santsg.tourvisio.dto.FlightSearchResponseItem) selectedItem).getAirline() + " flight";
        }
        
        String prompt = String.format(
            "You are a helpful travel assistant. The user has selected '%s' from the search results. " +
            "Please ask them politely if they would like to proceed with booking this option. " +
            "Ensure the response is natural and written in %s.",
            itemName, locale.getDisplayLanguage(Locale.ENGLISH));
            
        try {
            String aiResponse = geminiClient.generate(prompt);
            if (isValidResponse(aiResponse)) {
                return aiResponse.trim();
            }
        } catch (Exception e) {
            log.warn("[ResponseAgent] Confirm AI generation failed, using fallback: {}", e.getMessage());
        }
        
        return messageSource.getMessage("confirm.selection", new Object[]{itemName}, locale);
    }

    public String invalidDateRange(String errorType, SearchCriteria criteria, String userMessage) {
        Locale locale = resolveLocale(criteria);
        String targetLanguage = (criteria != null && criteria.getPreferredLanguage() != null) ? criteria.getPreferredLanguage() : "English";
        
        String context = "";
        if ("DATE_PAST".equals(errorType)) {
            context = "The user provided a check-in or departure date that is in the past. Explain that they must provide a future date.";
        } else if ("DATE_MISMATCH".equals(errorType)) {
            context = "The user provided a check-out or return date that is before the check-in or departure date. Explain that the return/check-out date must be after the start date.";
        }
        
        String prompt = String.format(
                "The user is planning a trip, but there is an issue with the dates. %s\n" +
                "Write a polite, helpful response explaining the specific error and asking them to correct the dates. " +
                "Do NOT say 'not found'. Respond directly to the user.\n" +
                "Write the response in the language of this user message: \"%s\" (Target: %s).\n" +
                "Return ONLY the response itself, no extra notes.",
                context, userMessage, targetLanguage
        );

        try {
            String aiResponse = geminiClient.generate(prompt);
            if (isValidResponse(aiResponse)) {
                return aiResponse.trim();
            }
        } catch (Exception e) {
            log.warn("[ResponseAgent] invalidDateRange AI generation failed: {}", e.getMessage());
        }

        String key = "DATE_PAST".equals(errorType) ? "invalid.date.past" : "invalid.date.mismatch";
        return messageSource.getMessage(key, null, locale);
    }

    public String noAdults(SearchCriteria criteria, String userMessage) {
        Locale locale = resolveLocale(criteria);
        String targetLanguage = (criteria != null && criteria.getPreferredLanguage() != null) ? criteria.getPreferredLanguage() : "English";

        String prompt = String.format(
                "The user is trying to book a hotel but has indicated 0 adults (only minors). " +
                "Write a polite response explaining that hotel reservations legally require at least one accompanying adult guest. " +
                "Write the response in the language of this user message: \"%s\" (Target: %s).\n" +
                "Return ONLY the response itself, no extra notes.",
                userMessage, targetLanguage
        );

        try {
            String aiResponse = geminiClient.generate(prompt);
            if (isValidResponse(aiResponse)) {
                return aiResponse.trim();
            }
        } catch (Exception e) {
            log.warn("[ResponseAgent] noAdults AI generation failed: {}", e.getMessage());
        }

        return messageSource.getMessage("error.no.adults", null, locale);
    }

    public String noResultsFound(SearchCriteria criteria, String userMessage) {
        Locale locale = resolveLocale(criteria);
        String targetLanguage = (criteria != null && criteria.getPreferredLanguage() != null) ? criteria.getPreferredLanguage() : "English";

        String location = criteria != null ? criteria.getLocationOrHotelName() : "the selected destination";
        String adults = criteria != null && criteria.getAdultCount() != null ? String.valueOf(criteria.getAdultCount()) : "?";
        String checkIn = criteria != null && criteria.getCheckInDate() != null ? criteria.getCheckInDate().toString() : "?";
        String checkOut = criteria != null && criteria.getCheckOutDate() != null ? criteria.getCheckOutDate().toString() : "?";
        
        String prompt = String.format(
                "The user searched for a trip (Location: %s, Dates: %s to %s, Adults: %s) but no results were found in the system.\n" +
                "Write a polite response. Start by briefly restating the key criteria (location, dates, guests) you understood from the user's request, then explain that no hotels or flights were found matching those exact criteria. " +
                "Never give a bare 'not found' message with zero context.\n" +
                "Write the response in the language of this user message: \"%s\" (Target: %s).\n" +
                "Return ONLY the response itself, no extra notes.",
                location, checkIn, checkOut, adults, userMessage, targetLanguage
        );

        try {
            String aiResponse = geminiClient.generate(prompt);
            if (isValidResponse(aiResponse)) {
                return aiResponse.trim();
            }
        } catch (Exception e) {
            log.warn("[ResponseAgent] noResultsFound AI generation failed: {}", e.getMessage());
        }

        String msgKey = "hotel.search.no.results";
        if (criteria != null && "FLIGHT_SEARCH".equals(criteria.getSearchType())) {
            msgKey = "flight.search.no.results";
        }
        String defaultMsg = messageSource.getMessage(msgKey, null, locale);
        if (criteria != null) {
            String details = String.format(" (Anlaşılan Kriterler: Konum: %s, Tarih: %s - %s, Yetişkin: %s, Çocuk: %s)",
                    criteria.getLocationOrHotelName() != null ? criteria.getLocationOrHotelName() : "?",
                    criteria.getCheckInDate() != null ? criteria.getCheckInDate() : "?",
                    criteria.getCheckOutDate() != null ? criteria.getCheckOutDate() : "?",
                    criteria.getAdultCount() != null ? criteria.getAdultCount() : "?",
                    criteria.getChildCount() != null ? criteria.getChildCount() : "0");
            return defaultMsg + details;
        }
        return defaultMsg;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private boolean isValidResponse(String response) {
        return response != null 
                && !response.trim().isEmpty() 
                && !response.trim().startsWith("[MOCK]")
                && !response.contains("Gemini service could not be reached");
    }

    private Locale resolveLocale(SearchCriteria criteria) {
        return com.santsg.tourvisio.util.LocaleResolver.resolveLocale(criteria);
    }

    /**
     * Locale kodunu (veya ülke adını) AI prompt'ları için okunabilir bir dil
     * adına çevirir. criteria.getPreferredLanguage() ham haliyle (ör. "Turkey")
     * prompt'a verildiğinde model karışabiliyor; bunun yerine dil adını kullanıyoruz.
     */
    private String resolveLanguageName(SearchCriteria criteria) {
        return com.santsg.tourvisio.util.LocaleResolver.resolveLanguageName(criteria);
    }

    private String getFieldKey(String field) {
        if (field == null) return null;
        switch (field.trim()) {
            case "konum veya otel adı": return "field.locationOrHotelName";
            case "giriş tarihi": return "field.checkInDate";
            case "çıkış tarihi": return "field.checkOutDate";
            case "yetişkin sayısı": return "field.adultCount";
            case "çocuk sayısı": return "field.childCount";
            case "çocuk yaşları": return "field.childAges";
            case "para birimi": return "field.currency";
            case "kalkış noktası": return "field.departureLocation";
            case "varış noktası": return "field.arrivalLocation";
            case "gidiş tarihi": return "field.departureDate";
            case "yolcu sayısı": return "field.passengerCount";
            case "tek yön / gidiş-dönüş": return "field.tripType";
            case "dönüş tarihi": return "field.returnDate";
            default: return null;
        }
    }
}
