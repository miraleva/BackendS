package com.santsg.tourvisio.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuthLoginRequest {

    /** OAuth provider: "google" */
    @NotBlank(message = "Provider is required")
    private String provider;

    /** The ID token received from the OAuth provider */
    @NotBlank(message = "ID token is required")
    private String idToken;
}
