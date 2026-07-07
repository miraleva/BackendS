package com.santsg.tourvisio.controller;

import com.santsg.tourvisio.dto.ChatRequest;
import com.santsg.tourvisio.dto.ChatResponse;
import com.santsg.tourvisio.service.ChatOrchestrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI Chatbot REST controller.
 *
 * <p>Tüm iş mantığı {@link ChatOrchestrationService}'e delege edilir.
 * Bu sınıf yalnızca HTTP katmanını yönetir.</p>
 */
@RestController
@RequestMapping("/api/chat")
@Tag(name = "Chat", description = "TourVisio AI Chatbot — otel ve uçak arama asistanı")
public class ChatController {

    private final ChatOrchestrationService orchestrationService;

    public ChatController(ChatOrchestrationService orchestrationService) {
        this.orchestrationService = orchestrationService;
    }

    /**
     * Kullanıcının chatbot'a mesaj gönderdiği ana endpoint.
     *
     * <p>Davranış özeti:</p>
     * <ul>
     *   <li>Mesajdan intent (HOTEL_SEARCH / FLIGHT_SEARCH) algılar.</li>
     *   <li>Eksik parametreler varsa kullanıcıya soru döner.</li>
     *   <li>Tüm bilgiler tamamsa AI yanıtı üretir ve ileride arama servisine yönlendirir.</li>
     *   <li>{@code sessionId} boş gönderilirse backend otomatik oluşturur ve response'da döner.</li>
     * </ul>
     */
    @PostMapping(
        value    = "/message",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
        summary     = "Chatbot'a mesaj gönder",
        description = """
            Kullanıcıdan gelen mesajı işler:
            1. Otel mi uçak mı arandığını algılar (intent detection).
            2. Eksik bilgiler varsa soru olarak geri döner.
            3. Tüm bilgiler tamamsa AI yanıtı üretir.
            
            **sessionId:** İlk istekte boş bırakın; backend UUID üretir ve response'da döner.
            Sonraki isteklerde aynı sessionId'yi gönderin.
            """
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description  = "Chatbot yanıtı başarıyla üretildi",
            content      = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema    = @Schema(implementation = ChatResponse.class),
                examples  = {
                    @ExampleObject(
                        name  = "Eksik parametre örneği",
                        value = """
                            {
                              "reply": "Arama yapabilmem için lütfen şu eksik bilgileri de belirtin: giriş tarihi, çıkış tarihi",
                              "sessionId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                              "searchType": "HOTEL_SEARCH",
                              "missingFields": ["giriş tarihi", "çıkış tarihi"],
                              "chatStatus": "ACTIVE"
                            }
                            """
                    ),
                    @ExampleObject(
                        name  = "Bilgiler tamam örneği",
                        value = """
                            {
                              "reply": "Antalya otel aramanız başlatıldı, en iyi seçenekleri listeliyorum...",
                              "sessionId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                              "searchType": "HOTEL_SEARCH",
                              "missingFields": [],
                              "chatStatus": "ACTIVE"
                            }
                            """
                    )
                }
            )
        ),
        @ApiResponse(responseCode = "400", description = "Geçersiz istek – mesaj boş olamaz")
    })
    public ResponseEntity<ChatResponse> sendMessage(
            @Valid @RequestBody ChatRequest request) {

        ChatResponse response = orchestrationService.orchestrate(request);
        return ResponseEntity.ok(response);
    }
}
