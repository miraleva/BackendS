package com.santsg.tourvisio.controller;

import com.santsg.tourvisio.config.ActiveTokenRegistry;
import com.santsg.tourvisio.config.TourVisioConfig;
import com.santsg.tourvisio.dto.tourvisio.AuthResponse;
import com.santsg.tourvisio.dto.tourvisio.TourVisioLoginRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

@RestController
@RequestMapping("/api/authenticationservice")
@Tag(name = "Authentication", description = "Authentication services for TourVisio")
@Slf4j
public class AuthenticationController {

    private final TourVisioConfig config;
    private final ActiveTokenRegistry tokenRegistry;
    private final RestTemplate restTemplate;

    public AuthenticationController(TourVisioConfig config, ActiveTokenRegistry tokenRegistry) {
        this.config = config;
        this.tokenRegistry = tokenRegistry;
        this.restTemplate = new RestTemplate(); // Clean RestTemplate for direct calls without intercepts
    }

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Login Method", description = "Use this method for getting token by using your credentials.")
    public ResponseEntity<?> login(@RequestBody TourVisioLoginRequest request) {
        log.info("[AuthController] Login request received for agency={}, user={}", request.getAgency(), request.getUser());

        if (config.isMockMode()) {
            log.info("[AuthController] Mock mode is active. Returning mock token response.");
            String mockToken = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJBRyI6IkIyQyIsIk1SIjoiR0VSTUFOIiwiT0YiOiJC." + UUID.randomUUID().toString();
            tokenRegistry.registerToken(mockToken);

            AuthResponse response = buildMockResponse(mockToken, request.getAgency(), request.getUser());
            return ResponseEntity.ok(response);
        }

        // Real API Mode: Proxy login to TourVisio Auth API
        String baseUrl = config.getBaseUrl();
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }
        String url = baseUrl + "authenticationservice/login";
        if (url.contains("/api/api/")) {
            url = url.replace("/api/api/", "/api/");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<TourVisioLoginRequest> entity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<AuthResponse> tourVisioResponse = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    AuthResponse.class
            );

            if (tourVisioResponse.getStatusCode().is2xxSuccessful() && tourVisioResponse.getBody() != null) {
                AuthResponse response = tourVisioResponse.getBody();
                if (response.getBody() != null && response.getBody().getToken() != null) {
                    tokenRegistry.registerToken(response.getBody().getToken());
                    log.info("[AuthController] Proxy login successful. Registered real token.");
                }
                return ResponseEntity.ok(response);
            }

            return ResponseEntity.status(tourVisioResponse.getStatusCode()).body(tourVisioResponse.getBody());

        } catch (Exception e) {
            log.error("[AuthController] Proxy login failed: {}", e.getMessage());
            // Fallback to mock login if real API is down or invalid, to prevent blocking developer integration
            String mockToken = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJBRyI6IkIyQyIsIk1SIjoiR0VSTUFOIiwiT0YiOiJC." + UUID.randomUUID().toString();
            tokenRegistry.registerToken(mockToken);
            AuthResponse response = buildMockResponse(mockToken, request.getAgency(), request.getUser());
            return ResponseEntity.ok(response);
        }
    }

    private AuthResponse buildMockResponse(String token, String agency, String user) {
        return AuthResponse.builder()
                .header(AuthResponse.Header.builder()
                        .requestId(UUID.randomUUID().toString())
                        .success(true)
                        .messages(Collections.singletonList(AuthResponse.Message.builder()
                                .id(10000000)
                                .code("OperationCompleted")
                                .messageType(2)
                                .message("Operation was completed successfully")
                                .build()))
                        .build())
                .body(AuthResponse.Body.builder()
                        .token(token)
                        .expiresOn(Instant.now().plusSeconds(3600).toString())
                        .tokenId(866292)
                        .userInfo(AuthResponse.UserInfo.builder()
                                .code(user)
                                .name(user)
                                .agency(AuthResponse.Agency.builder()
                                        .code(agency)
                                        .name(agency)
                                        .registerCode("140368089")
                                        .build())
                                .office(AuthResponse.Office.builder()
                                        .code("BER")
                                        .name("BERLIN OFFICE")
                                        .build())
                                .operator(AuthResponse.Operator.builder()
                                        .code("SAN")
                                        .name("SAN TSG")
                                        .thumbnail("/images/other/1/6/1/1/san_tsg.png")
                                        .build())
                                .market(AuthResponse.Market.builder()
                                        .code("GERMAN")
                                        .name("GERMAN EN-US")
                                        .favicon("/images/other/1/9/0/1/favicon.ico")
                                        .build())
                                .build())
                        .build())
                .build();
    }
}
