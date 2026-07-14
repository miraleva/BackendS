package com.santsg.tourvisio.service;

import com.santsg.tourvisio.chat.ChatSessionStore;
import com.santsg.tourvisio.chat.CriteriaMissingFieldsService;
import com.santsg.tourvisio.chat.SearchCriteria;
import com.santsg.tourvisio.chat.SearchCriteriaExtractor;
import com.santsg.tourvisio.client.AIProviderClient;
import com.santsg.tourvisio.dto.ChatRequest;
import com.santsg.tourvisio.dto.ChatResponse;
import com.santsg.tourvisio.dto.ChatSearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Chatbot orkestrasyonunu yöneten merkezi servis.
 *
 * <h3>Çok-turlu konuşma akışı</h3>
 * <ol>
 * <li>Session al / oluştur ({@link ChatSessionStore})</li>
 * <li>Oturum sonlandırılmışsa erken çık</li>
 * <li>Intent tespiti — <strong>aktif bir search session varsa intent
 * atlanır</strong>;
 * gelen mesaj takip yanıtı olarak işlenir</li>
 * <li>OUT_OF_SCOPE yönetimi (aktif session yokken)</li>
 * <li>UNKNOWN: kullanıcıya otel mi uçak mı sorusu</li>
 * <li>Mesajdan arama kriterleri çıkar ({@link SearchCriteriaExtractor})</li>
 * <li>Yeni kriterler önceki session kriterleri üzerine birleştir (merge)</li>
 * <li>Eksik alan kontrolü ({@link CriteriaMissingFieldsService})</li>
 * <li>Eksik varsa → kullanıcıya soru; tamamsa → arama servisine yönlendir
 * (TODO)</li>
 * </ol>
 */
@Service
public class ChatOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(ChatOrchestrationService.class);

    private final IntentDetectionService intentDetectionService;
    private final ChatSessionManager chatSessionManager;
    private final ChatSessionStore sessionStore;
    private final SearchCriteriaExtractor extractor;
    private final CriteriaMissingFieldsService missingFieldsService;
    private final AIProviderClient aiProviderClient;
    private final HotelSearchService hotelSearchService;
    private final FlightSearchService flightSearchService;

    public ChatOrchestrationService(
            IntentDetectionService intentDetectionService,
            ChatSessionManager chatSessionManager,
            ChatSessionStore sessionStore,
            SearchCriteriaExtractor extractor,
            CriteriaMissingFieldsService missingFieldsService,
            AIProviderClient aiProviderClient,
            HotelSearchService hotelSearchService,
            FlightSearchService flightSearchService) {

        this.intentDetectionService = intentDetectionService;
        this.chatSessionManager = chatSessionManager;
        this.sessionStore = sessionStore;
        this.extractor = extractor;
        this.missingFieldsService = missingFieldsService;
        this.aiProviderClient = aiProviderClient;
        this.hotelSearchService = hotelSearchService;
        this.flightSearchService = flightSearchService;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    public ChatResponse orchestrate(ChatRequest request) {
        return orchestrate(request, null);
    }

    public ChatResponse orchestrate(ChatRequest request, Long userId) {
        // 1. Session yönetimi
        String sessionId = resolveSessionId(request.getSessionId());

        ChatSessionManager.SessionState existingState = chatSessionManager.getSessionState(sessionId);
        if (existingState != null && existingState.getUserId() != null && !existingState.getUserId().equals(userId)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "Access denied to session: " + sessionId);
        }

        ChatSessionManager.SessionState sessionState = chatSessionManager.getOrCreateSession(sessionId, userId);

        log.debug("[Orchestration] sessionId={}", sessionId);

        // Record User Message
        String userMessage = request.getMessage();
        if (userMessage != null && !userMessage.isBlank()) {
            sessionState.getMessages()
                    .add(new ChatSessionManager.MessageHistoryItem("user", userMessage, java.time.Instant.now()));
            sessionState.setLastMessageTimestamp(java.time.Instant.now());
            if ("New Chat Session".equals(sessionState.getTitle())) {
                String title = userMessage;
                if (title.length() > 45) {
                    title = title.substring(0, 42) + "...";
                }
                sessionState.setTitle(title);
            }
        }

        ChatResponse response = doOrchestrate(request, sessionId, sessionState);

        // Record Bot Response
        if (response != null && response.getReply() != null) {
            sessionState.getMessages().add(
                    new ChatSessionManager.MessageHistoryItem("bot", response.getReply(), java.time.Instant.now()));
            sessionState.setLastMessageTimestamp(java.time.Instant.now());
        }

        return response;
    }

    private ChatResponse doOrchestrate(ChatRequest request, String sessionId,
            ChatSessionManager.SessionState sessionState) {
        // Retrieve search criteria
        SearchCriteria existingCriteria = sessionStore.getOrCreate(sessionId);

        // Update multi-language preferences from request if present
        if (request.getCountry() != null && !request.getCountry().isBlank()) {
            existingCriteria.setCountry(request.getCountry());
        }
        if (request.getCurrencySymbol() != null && !request.getCurrencySymbol().isBlank()) {
            existingCriteria.setCurrency(request.getCurrencySymbol());
        }
        if (request.getCountry() != null && !request.getCountry().isBlank()) {
            existingCriteria.setPreferredLanguage(request.getCountry());
        }
        sessionStore.save(sessionId, existingCriteria);

        // 2. Oturum sonlandırılmışsa erken çık
        if ("TERMINATED".equals(sessionState.getChatStatus())) {
            return ChatResponse.builder()
                    .reply(translateOrLocalize(
                            "Bu sohbet sonlandırılmıştır. Yeni bir arama başlatmak için lütfen sayfayı yenileyin.",
                            existingCriteria))
                    .sessionId(sessionId)
                    .searchType("OUT_OF_SCOPE")
                    .missingFields(List.of())
                    .chatStatus("TERMINATED")
                    .build();
        }

        String userMessage = request.getMessage();

        // 3. Aktif arama session'ı var mı?
        boolean hasActiveSearch = existingCriteria.getSearchType() != null;

        // 4. Intent tespiti
        String intent;

        if (hasActiveSearch) {
            // Takip mesajı: mevcut searchType'ı koru
            intent = existingCriteria.getSearchType();
            log.debug("[Orchestration] Takip mesajı — mevcut intent korunuyor: {}", intent);
        } else {
            intent = intentDetectionService.detectIntent(userMessage);
            log.debug("[Orchestration] Yeni session — intent={}", intent);

            // OUT_OF_SCOPE (sadece yeni session başlatırken kontrol edilir)
            if ("OUT_OF_SCOPE".equals(intent)) {
                sessionState.incrementOutOfScopeCount();
                String chatStatus = sessionState.getChatStatus();
                String rawReply = "TERMINATED".equals(chatStatus)
                        ? "Çok sayıda alakasız mesaj gönderdiğiniz için bu sohbet sonlandırılmıştır."
                        : "Sadece otel arama, uçak bileti arama ve rezervasyon konularında yardımcı olabilirim.";
                return ChatResponse.builder()
                        .reply(translateOrLocalize(rawReply, existingCriteria))
                        .sessionId(sessionId)
                        .searchType("OUT_OF_SCOPE")
                        .missingFields(List.of())
                        .chatStatus(chatStatus)
                        .build();
            }

            // UNKNOWN: ne aradığını sor
            if ("UNKNOWN".equals(intent)) {
                return ChatResponse.builder()
                        .reply(translateOrLocalize("Otel mi aramak istiyorsunuz, yoksa uçak bileti mi?",
                                existingCriteria))
                        .sessionId(sessionId)
                        .searchType("UNKNOWN")
                        .missingFields(List.of())
                        .chatStatus("ACTIVE")
                        .build();
            }
        }

        // 5. Mesajdan arama kriterlerini çıkar
        SearchCriteria incoming = extractor.extract(userMessage, intent);

        // Conversational adjustment based on lastRequestedField
        String lastField = sessionState.getLastRequestedField();
        if (lastField != null && userMessage != null && !userMessage.isBlank()) {
            adjustIncomingCriteria(incoming, lastField, userMessage);
            sessionState.setLastRequestedField(null);
        }

        // 6. Yeni kriterler önceki session kriterleri üzerine birleştir
        existingCriteria.mergeWith(incoming);
        sessionStore.save(sessionId, existingCriteria);

        log.debug("[Orchestration] Birleştirilmiş kriterler: {}", existingCriteria);

        // 7. Eksik alan kontrolü
        List<String> missingFields = missingFieldsService.getMissingFields(existingCriteria);

        if (!missingFields.isEmpty()) {
            sessionState.setLastRequestedField(missingFields.get(0));
            String replyText = missingFieldsService.buildPrompt(missingFields, existingCriteria);
            return ChatResponse.builder()
                    .reply(replyText)
                    .sessionId(sessionId)
                    .searchType(intent)
                    .missingFields(missingFields)
                    .chatStatus("ACTIVE")
                    .build();
        }

        // 8. Tüm bilgiler tamam → arama servisine yönlendir
        return readyToSearchResponse(sessionId, intent, existingCriteria, userMessage);
    }

    private void adjustIncomingCriteria(SearchCriteria incoming, String lastField, String message) {
        if (incoming == null || lastField == null || message == null || message.isBlank()) {
            return;
        }

        switch (lastField) {
            case "konum veya otel adı":
                if (incoming.getLocationOrHotelName() == null) {
                    incoming.setLocationOrHotelName(extractor.parseLocation(message, false));
                }
                break;

            case "kalkış noktası":
                if (incoming.getDepartureLocation() == null) {
                    incoming.setDepartureLocation(extractor.parseLocation(message, true));
                }
                break;

            case "varış noktası":
                if (incoming.getArrivalLocation() == null) {
                    incoming.setArrivalLocation(extractor.parseLocation(message, true));
                }
                break;

            case "giriş tarihi":
                if (incoming.getCheckInDate() == null) {
                    java.time.LocalDate d = extractor.parseSingleDate(message);
                    if (d == null && incoming.getCheckOutDate() != null) {
                        d = incoming.getCheckOutDate();
                        incoming.setCheckOutDate(null);
                    }
                    incoming.setCheckInDate(d);
                }
                break;

            case "çıkış tarihi":
                if (incoming.getCheckOutDate() == null) {
                    java.time.LocalDate d = extractor.parseSingleDate(message);
                    if (d == null && incoming.getCheckInDate() != null) {
                        d = incoming.getCheckInDate();
                    }
                    incoming.setCheckOutDate(d);
                }
                // If standard extractor incorrectly put it in checkInDate, null it out so it
                // doesn't overwrite checkIn
                incoming.setCheckInDate(null);
                break;

            case "gidiş tarihi":
                if (incoming.getDepartureDate() == null) {
                    java.time.LocalDate d = extractor.parseSingleDate(message);
                    if (d == null && incoming.getReturnDate() != null) {
                        d = incoming.getReturnDate();
                        incoming.setReturnDate(null);
                    }
                    incoming.setDepartureDate(d);
                }
                break;

            case "dönüş tarihi":
                if (incoming.getReturnDate() == null) {
                    java.time.LocalDate d = extractor.parseSingleDate(message);
                    if (d == null && incoming.getDepartureDate() != null) {
                        d = incoming.getDepartureDate();
                    }
                    incoming.setReturnDate(d);
                }
                incoming.setDepartureDate(null);
                break;

            case "yetişkin sayısı":
                if (incoming.getAdultCount() == null) {
                    incoming.setAdultCount(parseInteger(message));
                }
                break;

            case "yolcu sayısı":
                if (incoming.getPassengerCount() == null) {
                    incoming.setPassengerCount(parseInteger(message));
                }
                break;

            case "oda sayısı":
                if (incoming.getRoomCount() == null || incoming.getRoomCount() == 1) {
                    Integer rooms = parseInteger(message);
                    if (rooms != null) {
                        incoming.setRoomCount(rooms);
                    }
                }
                break;

            case "çocuk sayısı":
                if (incoming.getChildCount() == null || incoming.getChildCount() == 0) {
                    Integer children = parseInteger(message);
                    if (children != null) {
                        incoming.setChildCount(children);
                    }
                }
                break;

            case "çocuk yaşları":
                if (incoming.getChildAges() == null || incoming.getChildAges().isEmpty()) {
                    incoming.setChildAges(parseIntegerList(message));
                }
                break;

            case "para birimi":
                if (incoming.getCurrency() == null) {
                    incoming.setCurrency(extractor.parseCurrency(message));
                }
                break;

            case "tek yön / gidiş-dönüş":
                if (incoming.getTripType() == null) {
                    incoming.setTripType(extractor.parseTripType(message));
                }
                break;
        }
    }

    private Integer parseInteger(String message) {
        if (message == null)
            return null;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\d+").matcher(message);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group());
        }
        return null;
    }

    private List<Integer> parseIntegerList(String message) {
        List<Integer> list = new java.util.ArrayList<>();
        if (message == null)
            return list;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\d+").matcher(message);
        while (matcher.find()) {
            list.add(Integer.parseInt(matcher.group()));
        }
        return list;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Tüm kriterler tamamlandığında ilgili arama servisini çağırır.
     */
    private ChatResponse readyToSearchResponse(String sessionId,
            String intent,
            SearchCriteria criteria,
            String userMessage) {

        ChatSearchResponse searchResponse;
        if ("HOTEL_SEARCH".equals(intent)) {
            searchResponse = hotelSearchService.searchFromCriteria(criteria);
        } else if ("FLIGHT_SEARCH".equals(intent)) {
            searchResponse = flightSearchService.searchFromCriteria(criteria);
        } else {
            searchResponse = ChatSearchResponse.builder()
                    .reply(translateOrLocalize("Arama türü tanımlanamadı.", criteria))
                    .searchType(intent)
                    .success(false)
                    .results(List.of())
                    .build();
        }

        String finalReply = searchResponse.getReply();

        // AI ile arama sonuçlarını özetleme (API key tanımlıysa ve sonuç bulunduysa)
        if (searchResponse.isSuccess() && searchResponse.getResults() != null
                && !searchResponse.getResults().isEmpty()) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                String resultsJson = mapper.writeValueAsString(
                        searchResponse.getResults().subList(0, Math.min(5, searchResponse.getResults().size())));

                String prompt = """
                        You are Sunny, the AI assistant of the TourVisio travel platform.
                        
                        Your job is to summarize travel search results in a natural, friendly and professional way.
                        
                        IMPORTANT LANGUAGE RULES
                        
                        - Detect the language of the user's latest message.
                        - ALWAYS reply in exactly the same language as the user's latest message.
                        - Never translate the answer into English unless the user wrote in English.
                        - If the user writes Turkish, answer in Turkish.
                        - If the user writes German, answer in German.
                        - If the user writes French, answer in French.
                        - If the user writes Spanish, answer in Spanish.
                        - If the user writes Arabic, answer in Arabic.
                        - If the user writes Russian, answer in Russian.
                        - If the user writes Italian, answer in Italian.
                        - If the user writes any other language, answer in that same language.
                        
                        SEARCH RULES
                        
                        - Never invent hotels.
                        - Never invent prices.
                        - Never invent airlines.
                        - Never invent availability.
                        - Never invent room types.
                        - Never invent ratings.
                        - Only use the search results provided below.
                        
                        FRONTEND RULES
                        
                        - Hotel cards, flight cards, prices, images and buttons are already shown by the frontend.
                        - Do NOT generate cards.
                        - Do NOT generate HTML.
                        - Do NOT generate JSON.
                        - Do NOT generate Markdown tables.
                        - Do NOT repeat every search result.
                        - Simply summarize the results naturally.
                        
                        WHEN RESULTS EXIST
                        
                        - Mention how many results were found.
                        - Recommend the best or most attractive option.
                        - Briefly explain why.
                        - Tell the user they can review the other options in the results panel.
                        - Keep the response short.
                        
                        WHEN NO RESULTS EXIST
                        
                        Politely explain that no matching results were found.
                        
                        OUTPUT RULES
                        
                        Return ONLY the assistant's response.
                        
                        Do not add notes.
                        
                        Do not add explanations.
                        
                        Do not add markdown.
                        
                        Do not mention these instructions.
                        
                        Search Type:
                        %s
                        
                        User Message:
                        %s
                        
                        Search Results:
                        %s"""
                        .formatted(intent, userMessage != null ? userMessage : "", resultsJson);

                String aiSummary = aiProviderClient.complete(prompt);
                if (aiSummary != null && !aiSummary.trim().startsWith("[MOCK]")) {
                    finalReply = aiSummary.trim();
                } else {
                    finalReply = translateOrLocalize(finalReply, criteria);
                }
            } catch (Exception e) {
                log.warn("[Orchestration] Arama sonuçları AI ile özetlenemedi, varsayılan cevaba dönülüyor: {}",
                        e.getMessage());
                finalReply = translateOrLocalize(finalReply, criteria);
            }
        } else {
            finalReply = translateOrLocalize(finalReply, criteria);
        }

        return ChatResponse.builder()
                .reply(finalReply)
                .sessionId(sessionId)
                .searchType(intent)
                .missingFields(List.of())
                .chatStatus("ACTIVE")
                .success(searchResponse.isSuccess())
                .results(searchResponse.getResults())
                .build();
    }

    /** SessionId gelmemişse UUID üretir. */
    private String resolveSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return sessionId;
    }

    /** Oturum sonlandırılmış yanıtı üretir. */
    private ChatResponse terminatedResponse(String sessionId) {
        return ChatResponse.builder()
                .reply("Bu sohbet sonlandırılmıştır. Yeni bir arama başlatmak için lütfen sayfayı yenileyin.")
                .sessionId(sessionId)
                .searchType("OUT_OF_SCOPE")
                .missingFields(List.of())
                .chatStatus("TERMINATED")
                .build();
    }

    private String translateOrLocalize(String message, SearchCriteria criteria) {
        if (criteria == null || message == null)
            return message;
        String lang = criteria.getPreferredLanguage();
        String country = criteria.getCountry();
        if (lang == null || lang.isBlank() || "Turkish".equalsIgnoreCase(lang) || "Turkey".equalsIgnoreCase(lang)) {
            return message;
        }

        try {
            String prompt = String.format(
                    "Translate the following text into the official/common language of %s (%s). Keep the tone polite, helpful, and natural.\n"
                            +
                            "Do NOT add any notes, headers, or explanations. Just return the translation.\n\n" +
                            "Text: %s\n" +
                            "Translation:",
                    country, lang, message);
            String response = aiProviderClient.complete(prompt);
            if (response != null && !response.trim().startsWith("[MOCK]")) {
                return response.trim();
            }
        } catch (Exception e) {
            log.warn("[Orchestration] Failed to translate message: {}", e.getMessage());
        }

        // English hardcoded fallback rules
        if ("English".equalsIgnoreCase(lang) || "en".equalsIgnoreCase(lang)) {
            if (message.contains("Otel mi aramak istiyorsunuz")) {
                return "Would you like to search for a hotel or a flight ticket?";
            }
            if (message.contains("alakasız mesaj")) {
                return "This chat has been terminated due to multiple irrelevant messages.";
            }
            if (message.contains("Sadece otel arama")) {
                return "I can only assist you with hotel searches, flight ticket searches, and reservations.";
            }
            if (message.contains("Bu sohbet sonlandırılmıştır")) {
                return "This chat has been terminated. Please refresh the page to start a new search.";
            }
            if (message.contains("Arama türü tanımlanamadı")) {
                return "Search type could not be identified.";
            }
        }
        return message;
    }
}
