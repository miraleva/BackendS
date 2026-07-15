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
        String targetLanguage = (criteria != null && criteria.getPreferredLanguage() != null) ? criteria.getPreferredLanguage() : "English";
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

    public String clarify(SearchCriteria criteria) {
        Locale locale = resolveLocale(criteria);
        String targetLanguage = (criteria != null && criteria.getPreferredLanguage() != null) ? criteria.getPreferredLanguage() : "English";
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
        String targetLanguage = (criteria != null && criteria.getPreferredLanguage() != null) ? criteria.getPreferredLanguage() : "English";
        String targetCountry = (criteria != null && criteria.getCountry() != null) ? criteria.getCountry() : "United Kingdom";

        String fieldsCsv = String.join(", ", missingFields);
        String prompt = String.format(
                "The user is planning a trip, but the following mandatory search criteria are missing: [%s]. " +
                "Write a short, polite, and natural question asking the user to provide this missing information. " +
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

    public String summarize(String intent, String resultsJson, String defaultReply, SearchCriteria criteria) {
        Locale locale = resolveLocale(criteria);
        String targetLanguage = (criteria != null && criteria.getPreferredLanguage() != null) ? criteria.getPreferredLanguage() : "English";
        String targetCountry = (criteria != null && criteria.getCountry() != null) ? criteria.getCountry() : "United Kingdom";

        String prompt = String.format(
                "The user's travel search has been completed successfully. Here are the search results in JSON format:\n" +
                "Search Type: %s\n" +
                "Results:\n%s\n\n" +
                "Write a polite, engaging assistant response summarizing these results. Highlight the best options (price, stars, boards, airline/hotel, etc.). " +
                "Write the response in the official language of %s (%s). " +
                "Only mention facts from the provided JSON results. " +
                "Return ONLY the assistant's summary response, with no notes or extra text.",
                intent, resultsJson, targetCountry, targetLanguage
        );

        try {
            String aiResponse = geminiClient.generate(prompt);
            if (isValidResponse(aiResponse)) {
                return aiResponse.trim();
            }
        } catch (Exception e) {
            log.warn("[ResponseAgent] Summarize AI generation failed, using fallback localization: {}", e.getMessage());
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
        
        if ("tr".equals(locale.getLanguage())) {
            return String.format("%s için rezervasyon işlemine geçmek ister misiniz?", itemName);
        }
        return String.format("Would you like to proceed with booking %s?", itemName);
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
        if (criteria == null) {
            return Locale.ENGLISH;
        }
        String lang = criteria.getPreferredLanguage();
        if (lang == null || lang.isBlank()) {
            return Locale.ENGLISH;
        }
        String normalized = lang.trim().toLowerCase();
        if (normalized.startsWith("tr") || normalized.contains("turkish")) {
            return Locale.forLanguageTag("tr-TR");
        }
        if (normalized.startsWith("de") || normalized.contains("german")) {
            return Locale.GERMAN;
        }
        if (normalized.startsWith("ru") || normalized.contains("russian")) {
            return Locale.forLanguageTag("ru-RU");
        }
        if (normalized.startsWith("en") || normalized.contains("english")) {
            return Locale.ENGLISH;
        }
        return Locale.ENGLISH;
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
