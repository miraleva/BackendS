package com.santsg.tourvisio.agent;

import com.santsg.tourvisio.chat.SearchCriteria;
import com.santsg.tourvisio.client.AIFallbackChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Component
public class ResponseAgent {

    private static final Logger log = LoggerFactory.getLogger(ResponseAgent.class);

    /** Gemini → OpenRouter (ücretsiz) fallback zinciri. Bkz. {@code AIProviderConfig}. */
    private final AIFallbackChain geminiClient;
    private final MessageSource messageSource;

    public ResponseAgent(@Qualifier("responseAiChain") AIFallbackChain geminiClient, MessageSource messageSource) {
        this.geminiClient = geminiClient;
        this.messageSource = messageSource;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public Scenarios
    // ─────────────────────────────────────────────────────────────────────────

    public String decline(SearchCriteria criteria, boolean isTerminated) {
        return decline(criteria, isTerminated, null);
    }

    public String decline(SearchCriteria criteria, boolean isTerminated, String userMessage) {
        Locale locale = resolveLocale(criteria);
        String targetLanguage = resolveLanguageName(criteria);

        String prompt = String.format(
                "Write a polite travel assistant response declining the user's out-of-scope travel query. " +
                "Explain politely that we can only assist with hotel search, flight search, and booking/reservations. " +
                "Write the response in %s — the same language the user is writing in.%s " +
                "Context status: %s. " +
                "Return ONLY the response itself, no extra notes or introductions.",
                targetLanguage, userMessageClause(userMessage), isTerminated ? "TERMINATED" : "ACTIVE"
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
        // Hedef dili net bir isimle (ör. "English") sabitliyoruz — sadece "write in the
        // language of this message" demek, mesaj emoji/anlamsız metin gibi hiçbir dil
        // sinyali içermediğinde modelin rastgele bir dile (ör. İspanyolca) savrulmasına
        // yol açıyordu. Diğer tüm ResponseAgent metodları zaten somut bir "Target: X"
        // dili veriyor; welcome() de aynı şekilde davranmalı.
        String targetLanguage = "tr".equals(locale.getLanguage()) ? "Turkish"
                : "de".equals(locale.getLanguage()) ? "German"
                : "ru".equals(locale.getLanguage()) ? "Russian"
                : "English";
        String prompt = String.format(
                "The user just started a chat and sent their first message: \"%s\".\n" +
                "Write a warm, welcoming onboarding message as a travel assistant. " +
                "Briefly explain that you need their destination, dates, and guest count to find the best hotel or flight options. " +
                "Write the response in the same language as the user's message above. If the message has no " +
                "identifiable language (e.g. only emoji, random characters, numbers, or gibberish), default to %s — " +
                "do NOT guess an unrelated language.\n" +
                "Return ONLY the response itself, no extra notes or greetings.",
                userMessage != null ? userMessage.replace("\"", "\\\"") : "",
                targetLanguage
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
        return clarify(criteria, null);
    }

    public String clarify(SearchCriteria criteria, String userMessage) {
        Locale locale = resolveLocale(criteria);
        String targetLanguage = resolveLanguageName(criteria);

        String prompt = String.format(
                "Write a polite travel assistant question asking the user whether they would like to search for a hotel or a flight ticket. " +
                "Write the response in %s — the same language the user is writing in.%s " +
                "Return ONLY the question itself, no extra notes.",
                targetLanguage, userMessageClause(userMessage)
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
        return askMissing(missingFields, criteria, null);
    }

    public String askMissing(List<String> missingFields, SearchCriteria criteria, String userMessage) {
        Locale locale = resolveLocale(criteria);
        String targetLanguage = resolveLanguageName(criteria);

        String fieldsCsv = String.join(", ", missingFields);
        String prompt = String.format(
                "The user is planning a trip, but the following mandatory search criteria are missing: [%s]. " +
                "Ask the user for ALL of this information together in a single, friendly, and natural question. " +
                "Do NOT use bare technical terms (e.g., say 'How many people will be traveling?' instead of 'adult count'). " +
                "Write the question in %s — the same language the user is writing in.%s " +
                "Return ONLY the question itself, no extra notes.",
                fieldsCsv, targetLanguage, userMessageClause(userMessage)
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
                "1. Write the response in %s — the same language the user is writing in.%s\n" +
                "2. Only mention facts from the provided JSON results.\n" +
                "3. Never invent nicer names for raw system/sandbox data (e.g. if the room name is 'low level yerel dil' or 'BUILD131', present it exactly as is without fabricating a nicer name).\n" +
                "4. Return ONLY the assistant's summary response, with no notes or extra text.",
                intent, resultsJson, countNote, childNote, targetLanguage, userMessageClause(userMessage)
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
        return confirmSelection(selectedItem, criteria, null);
    }

    public String confirmSelection(Object selectedItem, SearchCriteria criteria, String userMessage) {
        Locale locale = resolveLocale(criteria);
        String targetLanguage = resolveLanguageName(criteria);
        String itemName = "";
        if (selectedItem instanceof com.santsg.tourvisio.dto.HotelSearchResponseItem) {
            itemName = ((com.santsg.tourvisio.dto.HotelSearchResponseItem) selectedItem).getName();
        } else if (selectedItem instanceof com.santsg.tourvisio.dto.FlightSearchResponseItem) {
            itemName = ((com.santsg.tourvisio.dto.FlightSearchResponseItem) selectedItem).getAirline() + " flight";
        }

        String prompt = String.format(
            "You are a helpful travel assistant. The user has selected '%s' from the search results. " +
            "Please ask them politely if they would like to proceed with booking this option. " +
            "Ensure the response is natural and written in %s — the same language the user is writing in.%s",
            itemName, targetLanguage, userMessageClause(userMessage));
            
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
        } else if ("DATE_TOO_FAR".equals(errorType)) {
            context = "The user provided a date more than 2 years in the future, which is unrealistic for a travel booking. Explain that they should choose a date within the next 2 years.";
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

        String key = "DATE_PAST".equals(errorType) ? "invalid.date.past"
                : "DATE_TOO_FAR".equals(errorType) ? "invalid.date.too.far"
                : "invalid.date.mismatch";
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

    /**
     * Negatif yolcu/misafir sayısı (ör. "-3 kişi") ya da izin verilen üst sınırı
     * (otelde 8, uçakta 9) aşan bir sayı girildiğinde çağrılır. Bilinçli olarak
     * serbest metinli bir yapay zeka çağrısı YAPMIYORUZ — anlamsız bir sayı,
     * modelin şaşırıp beklenmedik/alakasız bir dilde cevap vermesine yol
     * açabiliyordu; bunun yerine sabit, yerelleştirilmiş bir mesaj döndürülür.
     */
    public String invalidGuestCount(String errorType, SearchCriteria criteria) {
        Locale locale = resolveLocale(criteria);
        String key;
        Object[] args = null;
        switch (errorType) {
            case "NEGATIVE_COUNT":
                key = "error.negative.count";
                break;
            case "TOO_MANY_GUESTS":
                key = "error.too.many.guests";
                args = new Object[]{8};
                break;
            case "TOO_MANY_PASSENGERS":
                key = "error.too.many.passengers";
                args = new Object[]{9};
                break;
            case "TOO_MANY_ROOMS":
                key = "error.too.many.rooms";
                break;
            default:
                key = "error.negative.count";
        }
        return messageSource.getMessage(key, args, locale);
    }

    public String noResultsFound(SearchCriteria criteria, String userMessage) {
        return noResultsFound(criteria, userMessage, null);
    }

    public String noResultsFound(SearchCriteria criteria, String userMessage, java.util.List<String> suggestedDates) {
        Locale locale = resolveLocale(criteria);
        String targetLanguage = (criteria != null && criteria.getPreferredLanguage() != null) ? criteria.getPreferredLanguage() : "English";

        String location = criteria != null ? criteria.getLocationOrHotelName() : "the selected destination";
        String adults = criteria != null && criteria.getAdultCount() != null ? String.valueOf(criteria.getAdultCount()) : "?";
        String checkIn = criteria != null && criteria.getCheckInDate() != null ? criteria.getCheckInDate().toString() : "?";
        String checkOut = criteria != null && criteria.getCheckOutDate() != null ? criteria.getCheckOutDate().toString() : "?";
        boolean hasSuggestions = suggestedDates != null && !suggestedDates.isEmpty();
        String suggestedDatesText = hasSuggestions ? String.join(", ", suggestedDates) : null;
        // Sadece yetişkin sayısını yazınca, kullanıcı bir önceki turdan "sticky" kalmış
        // (o mesajda hiç bahsedilmemiş) çocuk/bebek sayısının hâlâ aramaya dahil
        // olduğunu fark edemiyordu — cevap "3 yetişkin için..." derken aslında arka
        // planda 3 bebek de aranıyor olabiliyordu. Şimdi tam misafir kompozisyonu
        // (yetişkin+çocuk+bebek) modele veriliyor ki cevap gerçek aramayı yansıtsın.
        String guestsDescription = describeGuestComposition(criteria);

        String prompt = String.format(
                "The user searched for a trip (Location: %s, Dates: %s to %s, Guests: %s) but no results were found in the system.\n" +
                "Write a polite response. Start by briefly restating the key criteria (location, dates, guests) you understood from the user's request, then explain that no hotels or flights were found matching those exact criteria. " +
                "Never give a bare 'not found' message with zero context. IMPORTANT: the Guests value already includes " +
                "children/infants carried over from earlier in the conversation even if the user's latest message didn't " +
                "mention them — state the FULL guest composition honestly (e.g. \"3 adults, 3 infants\"), do not silently " +
                "drop the children/infants just because this message only talked about adults.%s\n" +
                "Write the response in the language of this user message: \"%s\" (Target: %s).\n" +
                "Return ONLY the response itself, no extra notes.",
                location, checkIn, checkOut, guestsDescription,
                hasSuggestions
                        ? " The following nearby dates ARE available for this location, so suggest the user try one of them instead: " + suggestedDatesText + "."
                        : "",
                userMessage, targetLanguage
        );

        try {
            String aiResponse = geminiClient.generate(prompt);
            if (isValidResponse(aiResponse)) {
                return aiResponse.trim();
            }
        } catch (Exception e) {
            log.warn("[ResponseAgent] noResultsFound AI generation failed: {}", e.getMessage());
        }

        boolean isFlight = criteria != null && "FLIGHT_SEARCH".equals(criteria.getSearchType());
        String msgKey = isFlight ? "flight.search.no.results" : "hotel.search.no.results";
        String withDatesKey = isFlight ? "flight.search.no.results.with.dates" : "hotel.search.no.results.with.dates";
        String defaultMsg = hasSuggestions
                ? messageSource.getMessage(withDatesKey, new Object[]{suggestedDatesText}, locale)
                : messageSource.getMessage(msgKey, null, locale);
        if (criteria != null) {
            String locationParam = criteria.getLocationOrHotelName() != null ? criteria.getLocationOrHotelName() : "?";
            java.time.LocalDate startDate = isFlight ? criteria.getDepartureDate() : criteria.getCheckInDate();
            java.time.LocalDate endDate = isFlight ? criteria.getReturnDate() : criteria.getCheckOutDate();
            String datesParam = formatDisplayDate(startDate) + " - " + formatDisplayDate(endDate);
            String adultsParam = criteria.getAdultCount() != null ? criteria.getAdultCount().toString() : "?";
            String childrenParam = criteria.getChildCount() != null ? criteria.getChildCount().toString() : "0";
            String infantsParam = criteria.getInfantCount() != null ? criteria.getInfantCount().toString() : "0";

            String details = messageSource.getMessage("criteria.understood",
                new Object[]{locationParam, datesParam, adultsParam, childrenParam, infantsParam}, locale);
            return defaultMsg + " (" + details + ")";
        }
        return defaultMsg;
    }

    /**
     * "3 yetişkin, 2 çocuk, 1 bebek" tarzında tam misafir kompozisyonu metni üretir.
     * Sadece yetişkin sayısını gösteren cevaplar, önceki turdan "sticky" kalmış
     * (bu mesajda hiç bahsedilmemiş) çocuk/bebek sayısının hâlâ aramaya dahil
     * olduğunu kullanıcıdan gizlemiş oluyordu.
     */
    private String describeGuestComposition(SearchCriteria criteria) {
        if (criteria == null) return "?";
        List<String> parts = new java.util.ArrayList<>();
        if (criteria.getAdultCount() != null) parts.add(criteria.getAdultCount() + " adults");
        if (criteria.getChildCount() != null && criteria.getChildCount() > 0) parts.add(criteria.getChildCount() + " children");
        if (criteria.getInfantCount() != null && criteria.getInfantCount() > 0) parts.add(criteria.getInfantCount() + " infants");
        return parts.isEmpty() ? "?" : String.join(", ", parts);
    }

    public String noMoreResults(SearchCriteria criteria, String userMessage) {
        Locale locale = resolveLocale(criteria);
        String targetLanguage = (criteria != null && criteria.getPreferredLanguage() != null) ? criteria.getPreferredLanguage() : "English";

        String prompt = String.format(
                "The user is asking for more search results, but there are no further options available in the current search. " +
                "Write a polite response explaining that there are no additional results left to show for their current criteria, and suggest they might want to change their dates, location, or other preferences to see different options.\n" +
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
            log.warn("[ResponseAgent] noMoreResults AI generation failed: {}", e.getMessage());
        }

        // Fallback to the regular no results found message if AI fails
        return noResultsFound(criteria, userMessage);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static final java.time.format.DateTimeFormatter DISPLAY_DATE_FORMAT =
            java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private String formatDisplayDate(java.time.LocalDate date) {
        return date != null ? date.format(DISPLAY_DATE_FORMAT) : "?";
    }

    /**
     * Prompt'a kullanıcının ham mesajını ekleyerek dil talimatını somutlaştırır.
     * Sadece "hedef dil" adını söylemek (özellikle küçük/ücretsiz modellerde)
     * yetersiz kalabiliyor; mesajın kendisini de göstermek modelin doğru dili
     * seçmesini büyük ölçüde güçlendiriyor.
     */
    private String userMessageClause(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return "";
        }
        return " The user's exact message was: \"" + userMessage.replace("\"", "\\\"") + "\".";
    }

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
            case "konum veya otel adı":
            case "locationOrHotelName": return "field.locationOrHotelName";
            case "giriş tarihi":
            case "checkInDate": return "field.checkInDate";
            case "çıkış tarihi":
            case "checkOutDate": return "field.checkOutDate";
            case "yetişkin sayısı":
            case "adultCount": return "field.adultCount";
            case "çocuk sayısı":
            case "childCount": return "field.childCount";
            case "çocuk yaşları":
            case "childAges": return "field.childAges";
            case "bebek sayısı":
            case "infantCount": return "field.infantCount";
            case "bebek yaşları":
            case "infantAges": return "field.infantAges";
            case "para birimi":
            case "currency": return "field.currency";
            case "kalkış noktası":
            case "departureLocation": return "field.departureLocation";
            case "varış noktası":
            case "arrivalLocation": return "field.arrivalLocation";
            case "gidiş tarihi":
            case "departureDate": return "field.departureDate";
            case "yolcu sayısı":
            case "passengerCount": return "field.passengerCount";
            case "tek yön / gidiş-dönüş":
            case "tripType": return "field.tripType";
            case "dönüş tarihi":
            case "returnDate": return "field.returnDate";
            default: return null;
        }
    }
}
