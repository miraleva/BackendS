package com.santsg.tourvisio.dto.tourvisio;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * TourVisio Authentication login response DTO.
 *
 * <p>Beklenen response yapısı:</p>
 * <pre>
 * {
 *   "Header": { ... },
 *   "Body": {
 *     "token": "eyJhbGciOi..."
 *   }
 * }
 * </pre>
 *
 * <p>Token {@code body.token} path'inden çıkarılır.</p>
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TourVisioLoginResponse {

    @JsonProperty("Body")
    private Body body;

    /**
     * Yanıtın {@code Body} alanı.
     */
    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Body {

        /** Bearer token değeri */
        @JsonProperty("token")
        private String token;
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * {@code body.token} değerini döner; yoksa {@code null}.
     */
    public String extractToken() {
        if (body != null && body.getToken() != null && !body.getToken().isBlank()) {
            return body.getToken();
        }
        return null;
    }
}
