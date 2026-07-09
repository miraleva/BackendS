package com.santsg.tourvisio.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Kullanıcının chatbot'a gönderdiği mesaj isteği.
 * sessionId boş gelirse backend yeni bir oturum oluşturur.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Chatbot mesaj isteği")
public class ChatRequest {

    @NotBlank(message = "Kullanıcı mesajı boş olamaz")
    @Schema(description = "Kullanıcının chatbot'a yazdığı mesaj", example = "İstanbul'a uçak bileti arıyorum")
    private String message;

    @Schema(description = "Oturum kimliği. Boş gönderilirse yeni oturum başlatılır.", example = "session-abc-123")
    private String sessionId;

    @Schema(description = "Seçilen ülke veya bölge", example = "Turkey")
    private String country;

    @Schema(description = "Seçilen para birimi adı", example = "Turkish Lira")
    private String currencyName;

    @Schema(description = "Seçilen para birimi sembolü (ISO 4217)", example = "TRY")
    private String currencySymbol;

    public ChatRequest(String message, String sessionId) {
        this.message = message;
        this.sessionId = sessionId;
    }
}
