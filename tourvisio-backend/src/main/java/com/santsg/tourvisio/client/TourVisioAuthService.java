package com.santsg.tourvisio.client;

import com.santsg.tourvisio.config.TourVisioConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;

/**
 * TourVisio authentication servisi.
 *
 * <p>TourVisio API'si her isteğe Bearer token ister. Token, login endpointinden
 * agency/kullanıcı/şifre bilgileriyle alınır ve süresi dolana kadar cache'lenir.</p>
 *
 * <h3>Akış</h3>
 * <ol>
 *   <li>{@link #getToken()} çağrılır.</li>
 *   <li>Geçerli ve süresi dolmamış bir token varsa direkt döner.</li>
 *   <li>Yoksa {@link #login()} ile yeni token alınır.</li>
 * </ol>
 *
 * <p><strong>TODO:</strong> Gerçek TourVisio login endpoint path'i dokümandan
 * doğrulanınca {@code LOGIN_PATH} sabiti güncellenmelidir.</p>
 */
@Service
@Slf4j
public class TourVisioAuthService {

    /**
     * TourVisio login endpoint path'i.
     * TODO: Doküman gelince doğru path buraya yazılacak.
     *       Örnek: "/api/authenticationservice/login"
     */
    private static final String LOGIN_PATH = "/api/authenticationservice/login";

    /** Token'ın geçerlilik süresi (güvenlik marjıyla). Varsayılan 55 dakika. */
    private static final long TOKEN_TTL_SECONDS = 55 * 60;

    private final TourVisioConfig config;
    private final RestTemplate restTemplate;

    /** Cache'lenmiş token */
    private volatile String cachedToken;

    /** Token'ın alındığı an (epoch second) */
    private volatile long tokenObtainedAt;

    public TourVisioAuthService(TourVisioConfig config) {
        this.config = config;
        this.restTemplate = new RestTemplate();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Geçerli bir Bearer token döner. Süresi dolmuşsa otomatik yeniler.
     *
     * @return Bearer token string'i (başında "Bearer " yok)
     * @throws TourVisioAuthException login başarısız olursa
     */
    public synchronized String getToken() {
        if (cachedToken != null && !isExpired()) {
            return cachedToken;
        }
        return login();
    }

    /**
     * Cache'lenmiş token'ı siler; bir sonraki {@link #getToken()} çağrısında
     * yeniden login yapılmasını zorlar.
     */
    public synchronized void invalidateToken() {
        log.info("[TourVisioAuth] Token invalidated — next call will re-login.");
        cachedToken = null;
        tokenObtainedAt = 0;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Login
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * TourVisio login endpointine istek atar, dönen token'ı cache'ler.
     */
    private String login() {
        String url = config.getBaseUrl() + LOGIN_PATH;

        log.info("[TourVisioAuth] Logging in to TourVisio: {}", url);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // TourVisio login body
        Map<String, String> body = Map.of(
                "Agency", config.getAgency(),
                "User",   config.getUsername(),
                "Password", config.getPassword()
        );

        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new TourVisioAuthException(
                        "Login failed with status: " + response.getStatusCode());
            }

            Object tokenObj = extractToken(response.getBody());
            if (tokenObj == null) {
                throw new TourVisioAuthException(
                        "Login response does not contain a token field. Body: " + response.getBody());
            }

            cachedToken = tokenObj.toString();
            tokenObtainedAt = Instant.now().getEpochSecond();
            log.info("[TourVisioAuth] Login successful — token cached (TTL={}s).", TOKEN_TTL_SECONDS);

            return cachedToken;

        } catch (TourVisioAuthException e) {
            throw e;
        } catch (Exception e) {
            throw new TourVisioAuthException("TourVisio login failed: " + e.getMessage(), e);
        }
    }

    /**
     * Login response map'inden token değerini çıkarır.
     * TourVisio farklı format döndürebilir; bilinen yapılar denenir.
     */
    @SuppressWarnings("unchecked")
    private Object extractToken(Map<String, Object> responseBody) {
        // Düz seviye: { "Token": "abc..." }
        if (responseBody.containsKey("Token")) {
            return responseBody.get("Token");
        }
        // Nested: { "Body": { "Token": "abc..." } }
        Object bodyObj = responseBody.get("Body");
        if (bodyObj instanceof Map) {
            Map<String, Object> bodyMap = (Map<String, Object>) bodyObj;
            if (bodyMap.containsKey("Token")) {
                return bodyMap.get("Token");
            }
        }
        // Küçük harfli alternatifler
        if (responseBody.containsKey("token")) {
            return responseBody.get("token");
        }
        Object bodyLower = responseBody.get("body");
        if (bodyLower instanceof Map) {
            Map<String, Object> bodyMap = (Map<String, Object>) bodyLower;
            return bodyMap.get("token");
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private boolean isExpired() {
        return (Instant.now().getEpochSecond() - tokenObtainedAt) > TOKEN_TTL_SECONDS;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Custom exception
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * TourVisio login hatası.
     */
    public static class TourVisioAuthException extends RuntimeException {
        public TourVisioAuthException(String message) {
            super(message);
        }

        public TourVisioAuthException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
