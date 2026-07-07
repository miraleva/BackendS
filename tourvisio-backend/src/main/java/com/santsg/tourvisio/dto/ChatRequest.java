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
}
