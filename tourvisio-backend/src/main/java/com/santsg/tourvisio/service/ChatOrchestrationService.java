package com.santsg.tourvisio.service;

import com.santsg.tourvisio.client.AIProviderClient;
import com.santsg.tourvisio.dto.ChatRequest;
import com.santsg.tourvisio.dto.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Chatbot orkestrasyonunu yöneten merkezi servis.
 *
 * <p>Akış:</p>
 * <ol>
 *   <li>Oturumu al veya oluştur</li>
 *   <li>Oturum sonlandırılmışsa erken dön</li>
 *   <li>Intent tespiti ({@link IntentDetectionService})</li>
 *   <li>OUT_OF_SCOPE / UNKNOWN yönetimi</li>
 *   <li>Eksik parametre tespiti ({@link MissingParameterService})</li>
 *   <li>Bilgiler eksikse kullanıcıya soru sor</li>
 *   <li>Bilgiler tamsa AI ile zengin bir yanıt üret ({@link AIProviderClient})</li>
 *   <li>İleride: hotel/flight search servisine yönlendir</li>
 * </ol>
 *
 * <p>Bu sınıf rezervasyon işlemine karışmaz; yalnızca arama amacını
 * ve eksik parametreleri tespit eder.</p>
 */
@Service
public class ChatOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(ChatOrchestrationService.class);

    private final IntentDetectionService intentDetectionService;
    private final MissingParameterService missingParameterService;
    private final ChatSessionManager chatSessionManager;
    private final AIProviderClient aiProviderClient;

    public ChatOrchestrationService(IntentDetectionService intentDetectionService,
                                    MissingParameterService missingParameterService,
                                    ChatSessionManager chatSessionManager,
                                    AIProviderClient aiProviderClient) {
        this.intentDetectionService  = intentDetectionService;
        this.missingParameterService = missingParameterService;
        this.chatSessionManager      = chatSessionManager;
        this.aiProviderClient        = aiProviderClient;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Kullanıcıdan gelen mesajı işler ve chatbot yanıtını döner.
     *
     * @param request Kullanıcı mesajı ve (opsiyonel) oturum kimliği
     * @return Chatbot'un ürettiği {@link ChatResponse}
     */
    public ChatResponse orchestrate(ChatRequest request) {

        // 1. Session yönetimi
        String sessionId = resolveSessionId(request.getSessionId());
        ChatSessionManager.SessionState session = chatSessionManager.getOrCreateSession(sessionId);

        log.debug("[Orchestration] sessionId={} provider={}", sessionId, aiProviderClient.providerName());

        // 2. Oturum sonlandırılmışsa erken çık
        if ("TERMINATED".equals(session.getChatStatus())) {
            return terminatedResponse(sessionId);
        }

        String userMessage = request.getMessage();

        // 3. Intent tespiti
        String intent = intentDetectionService.detectIntent(userMessage);
        log.debug("[Orchestration] intent={}", intent);

        // 4. OUT_OF_SCOPE: kullanıcıyı uyar, 3. kez gelirse oturumu sonlandır
        if ("OUT_OF_SCOPE".equals(intent)) {
            session.incrementOutOfScopeCount();
            String chatStatus = session.getChatStatus();

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

        // 5. UNKNOWN: ne aradığını sor
        if ("UNKNOWN".equals(intent)) {
            return ChatResponse.builder()
                    .reply("Otel mi aramak istiyorsunuz, yoksa uçak bileti mi?")
                    .sessionId(sessionId)
                    .searchType("UNKNOWN")
                    .missingFields(List.of())
                    .chatStatus("ACTIVE")
                    .build();
        }

        // 6. Eksik parametre tespiti (HOTEL_SEARCH veya FLIGHT_SEARCH)
        List<String> missingFields = missingParameterService.getMissingParameters(intent, userMessage);

        if (!missingFields.isEmpty()) {
            // Çocuk yaşı özel mesajı
            String replyText = missingFields.contains("childAges")
                    ? "Çocukların yaşlarını belirtir misiniz?"
                    : missingParameterService.generateMissingFieldsPrompt(missingFields);

            return ChatResponse.builder()
                    .reply(replyText)
                    .sessionId(sessionId)
                    .searchType(intent)
                    .missingFields(missingFields)
                    .chatStatus("ACTIVE")
                    .build();
        }

        // 7. Tüm bilgiler tamam → AI ile yanıt üret
        //    (İleride bu noktada hotel/flight search servisine yönlendirme yapılacak)
        String aiReply = generateAIReply(intent, userMessage);

        return ChatResponse.builder()
                .reply(aiReply)
                .sessionId(sessionId)
                .searchType(intent)
                .missingFields(List.of())
                .chatStatus("ACTIVE")
                .build();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * SessionId gelmemişse UUID ile yeni bir tane üretir.
     * Bu ID response içinde frontend'e dönülür; sonraki isteklerde kullanılır.
     */
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

    /**
     * Tüm arama parametreleri tamamlandığında AI'dan yardımcı bir yanıt üretir.
     * Hotel veya Flight search servisine yönlendirme bu metodun hemen altına eklenecek.
     */
    private String generateAIReply(String intent, String userMessage) {
        String searchLabel = "HOTEL_SEARCH".equals(intent) ? "otel" : "uçak";
        String prompt = String.format(
            "Kullanıcı %s araması yapmak istiyor. Mesaj: '%s'. "
            + "Aramanın başlatıldığını belirten kısa ve samimi bir Türkçe cevap yaz.",
            searchLabel, userMessage
        );
        return aiProviderClient.complete(prompt);
    }
}
