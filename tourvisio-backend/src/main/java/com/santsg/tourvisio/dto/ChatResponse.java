package com.santsg.tourvisio.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Chatbot'un kullanıcıya döndürdüğü cevap modeli.
 * <ul>
 *   <li>{@code reply}        – Kullanıcıya gösterilecek metin</li>
 *   <li>{@code sessionId}    – Oturum kimliği (yeni oluşturulduysa buradan okunur)</li>
 *   <li>{@code searchType}   – Algılanan arama türü: HOTEL_SEARCH | FLIGHT_SEARCH | UNKNOWN | OUT_OF_SCOPE</li>
 *   <li>{@code missingFields} – Aramayı tamamlamak için eksik olan parametreler</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Chatbot cevap modeli")
public class ChatResponse {

    @Schema(description = "Kullanıcıya gösterilecek chatbot cevabı",
            example = "Antalya'ya uçak aramanız için hangi tarihte gitmek istiyorsunuz?")
    private String reply;

    @Schema(description = "Oturum kimliği", example = "session-abc-123")
    private String sessionId;

    @Schema(description = "Algılanan arama tipi",
            allowableValues = {"HOTEL_SEARCH", "FLIGHT_SEARCH", "UNKNOWN", "OUT_OF_SCOPE"},
            example = "FLIGHT_SEARCH")
    private String searchType;

    @Schema(description = "Aramayı tamamlamak için eksik alanların listesi",
            example = "[\"gidiş tarihi\", \"yolcu sayısı\"]")
    private List<String> missingFields;

    @Schema(description = "Oturum durumu: ACTIVE veya TERMINATED", example = "ACTIVE")
    private String chatStatus;
}
