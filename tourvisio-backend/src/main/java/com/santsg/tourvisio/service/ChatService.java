package com.santsg.tourvisio.service;

import com.santsg.tourvisio.dto.ChatMessageRequest;
import com.santsg.tourvisio.dto.ChatMessageResponse;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ChatService {

    private final IntentDetectionService intentDetectionService;
    private final MissingParameterService missingParameterService;
    private final RagService ragService;
    private final ChatSessionManager chatSessionManager;

    public ChatService(IntentDetectionService intentDetectionService, 
                       MissingParameterService missingParameterService, 
                       RagService ragService,
                       ChatSessionManager chatSessionManager) {
        this.intentDetectionService = intentDetectionService;
        this.missingParameterService = missingParameterService;
        this.ragService = ragService;
        this.chatSessionManager = chatSessionManager;
    }

    public ChatMessageResponse processMessage(ChatMessageRequest request) {
        String sessionId = request.getSessionId();
        String userMessage = request.getUserMessage();
        
        // 1. Get or create session state
        ChatSessionManager.SessionState sessionState = chatSessionManager.getOrCreateSession(sessionId);
        
        // 2. Check if the session is already terminated
        if ("TERMINATED".equals(sessionState.getChatStatus())) {
            return ChatMessageResponse.builder()
                    .intent("OUT_OF_SCOPE")
                    .missingFields(List.of())
                    .botMessage("Bu sohbet sonlandırılmıştır.")
                    .chatStatus("TERMINATED")
                    .build();
        }
        
        // 3. Detect intent
        String intent = intentDetectionService.detectIntent(userMessage);
        
        // 4. Handle OUT_OF_SCOPE intent
        if ("OUT_OF_SCOPE".equals(intent)) {
            sessionState.incrementOutOfScopeCount();
            
            String chatStatus = sessionState.getChatStatus();
            String botMsg = "Sadece otel arama, uçak arama ve rezervasyon konularında yardımcı olabilirim.";
            if ("TERMINATED".equals(chatStatus)) {
                botMsg = "Çok sayıda alakasız mesaj gönderdiğiniz için bu sohbet sonlandırılmıştır.";
            }
            
            return ChatMessageResponse.builder()
                    .intent("OUT_OF_SCOPE")
                    .missingFields(List.of())
                    .botMessage(botMsg)
                    .chatStatus(chatStatus)
                    .build();
        }
        
        // 5. Handle UNKNOWN intent
        if ("UNKNOWN".equals(intent)) {
            return ChatMessageResponse.builder()
                    .intent("UNKNOWN")
                    .missingFields(List.of())
                    .botMessage("Otel mi uçak mı aramak istiyorsunuz?")
                    .chatStatus("ACTIVE")
                    .build();
        }
        
        // 6. Detect missing parameters
        List<String> missingFields = missingParameterService.getMissingParameters(intent, userMessage);
        
        String botMessage;
        if (!missingFields.isEmpty()) {
            // Override message if child ages are missing
            if (missingFields.contains("childAges")) {
                botMessage = "Çocuk yaşı nedir?";
            } else {
                botMessage = missingParameterService.generateMissingFieldsPrompt(missingFields);
            }
        } else {
            // 7. Retrieve contexts and run mock generator
            String ragResponse = ragService.retrieveAndGenerate(userMessage);
            botMessage = "Tüm bilgiler eksiksiz alındı. Arama kriterlerinize uygun sonuçlar hazırlanıyor...\n" + ragResponse;
        }

        return ChatMessageResponse.builder()
                .intent(intent)
                .missingFields(missingFields)
                .botMessage(botMessage)
                .chatStatus("ACTIVE")
                .build();
    }
}
