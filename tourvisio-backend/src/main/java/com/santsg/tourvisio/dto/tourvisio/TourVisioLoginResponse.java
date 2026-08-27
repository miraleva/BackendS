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

    @JsonProperty("body")
    @com.fasterxml.jackson.annotation.JsonAlias({"Body", "body", "BODY", "result", "Result", "data", "Data"})
    private Body body;

    @JsonProperty("token")
    @com.fasterxml.jackson.annotation.JsonAlias({"Token", "token", "TOKEN"})
    private String rootToken;

    /**
     * Yanıtın {@code Body} alanı.
     */
    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Body {

        /** Bearer token değeri */
        @JsonProperty("token")
        @com.fasterxml.jackson.annotation.JsonAlias({"Token", "token", "TOKEN", "access_token", "accessToken"})
        private String token;
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * {@code body.token} veya kök {@code token} değerini döner; yoksa {@code null}.
     */
    public String extractToken() {
        if (body != null && body.getToken() != null && !body.getToken().isBlank()) {
            return body.getToken();
        }
        if (rootToken != null && !rootToken.isBlank()) {
            return rootToken;
        }
        return null;
    }
}
