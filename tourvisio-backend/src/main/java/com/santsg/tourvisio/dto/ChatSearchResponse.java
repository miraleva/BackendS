package com.santsg.tourvisio.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Arama tamamlandığında chatbot'un döndürdüğü sonuç modeli.
 * <ul>
 *   <li>{@code reply}      – Kullanıcıya gösterilecek özet metin</li>
 *   <li>{@code searchType} – HOTEL_SEARCH | FLIGHT_SEARCH</li>
 *   <li>{@code success}    – Arama başarılı mı?</li>
 *   <li>{@code results}    – Bulunan sonuçlar (generic list)</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Chat arama sonuç modeli")
public class ChatSearchResponse {

    @Schema(description = "Kullanıcıya gösterilecek özet metin",
            example = "Antalya için uygun oteller bulundu.")
    @JsonProperty("reply")
    private String reply;

    @Schema(description = "Arama tipi",
            allowableValues = {"HOTEL_SEARCH", "FLIGHT_SEARCH"},
            example = "HOTEL_SEARCH")
    @JsonProperty("searchType")
    private String searchType;

    @Schema(description = "Arama başarılı mı?", example = "true")
    @JsonProperty("success")
    private boolean success;

    @Schema(description = "Bulunan sonuçların listesi")
    @JsonProperty("results")
    private List<?> results;
}
