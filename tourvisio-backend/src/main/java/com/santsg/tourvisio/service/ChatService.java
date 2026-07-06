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

    public ChatService(IntentDetectionService intentDetectionService, 
                       MissingParameterService missingParameterService, 
                       RagService ragService) {
        this.intentDetectionService = intentDetectionService;
        this.missingParameterService = missingParameterService;
        this.ragService = ragService;
    }

    public ChatMessageResponse processMessage(ChatMessageRequest request) {
        String userMessage = request.getUserMessage();
        
        // 1. Detect Intent
        String intent = intentDetectionService.detectIntent(userMessage);
        
        // 2. Handle UNKNOWN intent
        if ("UNKNOWN".equals(intent)) {
            return ChatMessageResponse.builder()
                    .intent("UNKNOWN")
                    .missingFields(List.of())
                    .botMessage("Otel mi uçak mı aramak istiyorsunuz?")
                    .build();
        }
        
        // 3. Detect missing parameters
        List<String> missingFields = missingParameterService.getMissingParameters(intent, userMessage);
        
        String botMessage;
        if (!missingFields.isEmpty()) {
            // 4. Construct prompt to ask for missing parameters
            botMessage = missingParameterService.generateMissingFieldsPrompt(missingFields);
        } else {
            // 5. If all parameters are present, return RAG-generated confirmation message
            String ragResponse = ragService.retrieveAndGenerate(userMessage);
            botMessage = "Tüm bilgiler eksiksiz alındı. Arama kriterlerinize uygun sonuçlar hazırlanıyor...\n" + ragResponse;
        }

        return ChatMessageResponse.builder()
                .intent(intent)
                .missingFields(missingFields)
                .botMessage(botMessage)
                .build();
    }
}
