package com.santsg.tourvisio.service;

import com.santsg.tourvisio.chat.ChatSessionStore;
import com.santsg.tourvisio.chat.CriteriaMissingFieldsService;
import com.santsg.tourvisio.chat.SearchCriteria;
import com.santsg.tourvisio.chat.SearchCriteriaExtractor;
import com.santsg.tourvisio.client.AIProviderClient;
import com.santsg.tourvisio.dto.ChatRequest;
import com.santsg.tourvisio.dto.ChatResponse;
import com.santsg.tourvisio.dto.FlightSearchRequest;
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

        // 2. Oturum sonlandırılmışsa erken çık
        if ("TERMINATED".equals(sessionState.getChatStatus())) {
            return terminatedResponse(sessionId);
        }

        String userMessage = request.getMessage();

        // 3. Aktif arama session'ı var mı?
        SearchCriteria existingCriteria = sessionStore.getOrCreate(sessionId);
        boolean hasActiveSearch = existingCriteria.getSearchType() != null;

        // 4. Intent tespiti
        //    - Aktif bir search session varsa intent'i yeniden hesaplamaya gerek yok;
        //      gelen mesaj doğrudan o session'a ait takip mesajıdır.
        //    - Aktif session yoksa klasik intent detection devreye girer.
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
                String reply = "TERMINATED".equals(chatStatus)
                        ? "Çok sayıda alakasız mesaj gönderdiğiniz için bu sohbet sonlandırılmıştır."
                        : "Sadece otel arama, uçak bileti arama ve rezervasyon konularında yardımcı olabilirim.";
                return ChatResponse.builder()
                        .reply(reply)
                        .sessionId(sessionId)
                        .searchType("OUT_OF_SCOPE")
                        .missingFields(List.of())
                        .chatStatus(chatStatus)
                        .build();
            }

            // UNKNOWN: ne aradığını sor
            if ("UNKNOWN".equals(intent)) {
                return ChatResponse.builder()
                        .reply("Otel mi aramak istiyorsunuz, yoksa uçak bileti mi?")
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
            String replyText = missingFieldsService.buildPrompt(missingFields);
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
     * Tüm kriterler tamamlandığında döner.
     */
    private ChatResponse readyToSearchResponse(String sessionId,
                                               String intent,
                                               SearchCriteria criteria) {

        String label = "HOTEL_SEARCH".equals(intent) ? "Otel" : "Uçak";
        String reply;

        if ("HOTEL_SEARCH".equals(intent)) {
            com.santsg.tourvisio.dto.hotel.HotelSearchRequest hotelRequest = criteria.toHotelSearchRequestDto();
            log.info("[Orchestration] HotelSearchRequest oluşturuldu ve hazırlandı: {}", hotelRequest);
            reply = "Otel araması için gerekli bilgiler tamamlandı. Arama servisine yönlendiriliyor. HotelSearchRequest hazırlandı.";
        } else {
            FlightSearchRequest flightRequest = criteria.toFlightSearchRequest();
            log.info("[Orchestration] FlightSearchRequest oluşturuldu ve hazırlandı: {}", flightRequest);
            reply = "Uçak araması için gerekli bilgiler tamamlandı. Arama servisine yönlendiriliyor. FlightSearchRequest hazırlandı.";
        }

        // Aramanın gerçekten başladığı anlama gelir
        sessionStore.remove(sessionId);

        return ChatResponse.builder()
                .reply(reply)
                .sessionId(sessionId)
                .searchType(intent)
                .missingFields(List.of())
                .chatStatus("ACTIVE")
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
}
