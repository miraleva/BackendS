package com.santsg.tourvisio.dto.tourvisio;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
    @NotBlank(message = "Agency cannot be blank")
    @Size(max = 100, message = "Agency cannot exceed 100 characters")
    @JsonProperty("Agency")
    private String agency;

    /** Login kullanıcı adı (env: TOURVISIO_USERNAME) */
    @NotBlank(message = "User cannot be blank")
    @Size(max = 100, message = "User cannot exceed 100 characters")
    @JsonProperty("User")
    private String user;

    /** Login şifresi (env: TOURVISIO_PASSWORD) */
    @NotBlank(message = "Password cannot be blank")
    @Size(max = 100, message = "Password cannot exceed 100 characters")
    @JsonProperty("Password")
    private String password;
}

