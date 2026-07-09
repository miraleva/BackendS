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
 *   <li>Session al / oluştur ({@link ChatSessionStore})</li>
 *   <li>Oturum sonlandırılmışsa erken çık</li>
 *   <li>Intent tespiti — <strong>aktif bir search session varsa intent atlanır</strong>;
 *       gelen mesaj takip yanıtı olarak işlenir</li>
 *   <li>OUT_OF_SCOPE yönetimi (aktif session yokken)</li>
 *   <li>UNKNOWN: kullanıcıya otel mi uçak mı sorusu</li>
 *   <li>Mesajdan arama kriterleri çıkar ({@link SearchCriteriaExtractor})</li>
 *   <li>Yeni kriterler önceki session kriterleri üzerine birleştir (merge)</li>
 *   <li>Eksik alan kontrolü ({@link CriteriaMissingFieldsService})</li>
 *   <li>Eksik varsa → kullanıcıya soru; tamamsa → arama servisine yönlendir (TODO)</li>
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
        this.chatSessionManager     = chatSessionManager;
        this.sessionStore           = sessionStore;
        this.extractor              = extractor;
        this.missingFieldsService   = missingFieldsService;
        this.aiProviderClient       = aiProviderClient;
        this.hotelSearchService     = hotelSearchService;
        this.flightSearchService    = flightSearchService;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    public ChatResponse orchestrate(ChatRequest request) {

        // 1. Session yönetimi
        String sessionId = resolveSessionId(request.getSessionId());
        ChatSessionManager.SessionState sessionState =
                chatSessionManager.getOrCreateSession(sessionId);

        log.debug("[Orchestration] sessionId={}", sessionId);

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
                    .reply(translateOrLocalize("Bu sohbet sonlandırılmıştır. Yeni bir arama başlatmak için lütfen sayfayı yenileyin.", existingCriteria))
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
                        .reply(translateOrLocalize("Otel mi aramak istiyorsunuz, yoksa uçak bileti mi?", existingCriteria))
                        .sessionId(sessionId)
                        .searchType("UNKNOWN")
                        .missingFields(List.of())
                        .chatStatus("ACTIVE")
                        .build();
            }
        }

        // 5. Mesajdan arama kriterlerini çıkar
        SearchCriteria incoming = extractor.extract(userMessage, intent);

        // 6. Yeni kriterler önceki session kriterleri üzerine birleştir
        existingCriteria.mergeWith(incoming);
        sessionStore.save(sessionId, existingCriteria);

        log.debug("[Orchestration] Birleştirilmiş kriterler: {}", existingCriteria);

        // 7. Eksik alan kontrolü
        List<String> missingFields = missingFieldsService.getMissingFields(existingCriteria);

        if (!missingFields.isEmpty()) {
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
        return readyToSearchResponse(sessionId, intent, existingCriteria);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Tüm kriterler tamamlandığında ilgili arama servisini çağırır.
     */
    private ChatResponse readyToSearchResponse(String sessionId,
                                               String intent,
                                               SearchCriteria criteria) {

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
        if (searchResponse.isSuccess() && searchResponse.getResults() != null && !searchResponse.getResults().isEmpty()) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                String resultsJson = mapper.writeValueAsString(
                        searchResponse.getResults().subList(0, Math.min(5, searchResponse.getResults().size()))
                );

                String lang = (criteria.getPreferredLanguage() != null) ? criteria.getPreferredLanguage() : "Turkish";
                String country = (criteria.getCountry() != null) ? criteria.getCountry() : "Turkey";

                String prompt = """
                        The user's travel search has been successfully completed, and the following results were found:
                        Search Type: %s
                        Results:
                        %s
                        
                        Write a polite assistant response summarizing these results, highlighting the best/most attractive options and travel details (price, airline, stars, board type, etc.) in the official/most common language of %s (%s).
                        Use real prices and information from the API response.
                        Return ONLY the assistant's response, with no notes or extra text.
                        Assistant Response:""".formatted(intent, resultsJson, country, lang);

                String aiSummary = aiProviderClient.complete(prompt);
                if (aiSummary != null && !aiSummary.trim().startsWith("[MOCK]")) {
                    finalReply = aiSummary.trim();
                } else {
                    finalReply = translateOrLocalize(finalReply, criteria);
                }
            } catch (Exception e) {
                log.warn("[Orchestration] Arama sonuçları AI ile özetlenemedi, varsayılan cevaba dönülüyor: {}", e.getMessage());
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
        if (criteria == null || message == null) return message;
        String lang = criteria.getPreferredLanguage();
        String country = criteria.getCountry();
        if (lang == null || lang.isBlank() || "Turkish".equalsIgnoreCase(lang) || "Turkey".equalsIgnoreCase(lang)) {
            return message;
        }

        try {
            String prompt = String.format(
                    "Translate the following text into the official/common language of %s (%s). Keep the tone polite, helpful, and natural.\n" +
                    "Do NOT add any notes, headers, or explanations. Just return the translation.\n\n" +
                    "Text: %s\n" +
                    "Translation:",
                    country, lang, message
            );
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
