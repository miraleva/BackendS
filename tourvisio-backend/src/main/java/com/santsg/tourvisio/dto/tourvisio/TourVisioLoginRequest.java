package com.santsg.tourvisio.dto.tourvisio;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * TourVisio Authentication login request DTO.
 *
 * <p>POST /api/authenticationservice/login için kullanılır.</p>
 *
 * <pre>
 * {
 *   "Agency":   "AGENCY_CODE",
 *   "User":     "username",
 *   "Password": "secret"
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TourVisioLoginRequest {

    /** TourVisio agency kodu (env: TOURVISIO_AGENCY) */
    @JsonProperty("Agency")
    private String agency;

    /** Login kullanıcı adı (env: TOURVISIO_USERNAME) */
    @JsonProperty("User")
    private String user;

    /** Login şifresi (env: TOURVISIO_PASSWORD) */
    @JsonProperty("Password")
    private String password;
}
