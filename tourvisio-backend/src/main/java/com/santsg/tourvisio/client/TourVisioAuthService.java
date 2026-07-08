package com.santsg.tourvisio.client;

import com.santsg.tourvisio.config.TourVisioConfig;
import com.santsg.tourvisio.dto.tourvisio.TourVisioLoginRequest;
import com.santsg.tourvisio.dto.tourvisio.TourVisioLoginResponse;
import com.santsg.tourvisio.exception.TourVisioAuthException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;

/**
 * TourVisio authentication servisi.
 *
 * <h3>Akış</h3>
 * <ol>
 *   <li>{@link #getToken()} çağrılır.</li>
 *   <li>Geçerli ve süresi dolmamış bir token cache'de varsa direkt döner.</li>
 *   <li>Yoksa {@link #login()} ile POST /api/authenticationservice/login çağrılır.</li>
 *   <li>Response'daki {@code body.token} değeri cache'lenir ve döner.</li>
 * </ol>
 *
 * <h3>Token yenileme</h3>
 * <p>Token süresi dolduğunda (varsayılan 55 dakika) bir sonraki {@link #getToken()}
 * çağrısında otomatik olarak yeniden login yapılır.
 * 401 alan client'lar {@link #invalidateToken()} çağırarak erkenden yenileyebilir.</p>
 *
 * <h3>Thread safety</h3>
 * <p>{@link #getToken()} ve {@link #invalidateToken()} {@code synchronized} olduğundan
 * birden fazla thread aynı anda login yapmaz.</p>
 */
@Service
@Slf4j
public class TourVisioAuthService {

    /** TourVisio login endpoint path'i — TourVisio dokümantasyonuna göre. */
    private static final String LOGIN_PATH = "/api/authenticationservice/login";

    /**
     * Token'ın geçerlilik süresi — güvenlik marjı olarak 55 dakika kullanılır
     * (TourVisio token'ı genellikle 60 dakika geçerlidir).
     */
    private static final long TOKEN_TTL_SECONDS = 55 * 60L;

    private final TourVisioConfig config;
    private final RestTemplate restTemplate;

    /** Cache'lenmiş token (null ise henüz alınmamış veya süresi dolmuş) */
    private volatile String cachedToken;

    /** Token'ın alındığı an (epoch second) */
    private volatile long tokenObtainedAt;

    public TourVisioAuthService(TourVisioConfig config,
                                @Qualifier("tourVisioRestTemplate") RestTemplate restTemplate) {
        this.config = config;
        this.restTemplate = restTemplate;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Geçerli bir Bearer token döner. Süresi dolmuşsa otomatik yeniler.
     *
     * @return Bearer token string'i (başında "Bearer " prefix'i yok)
     * @throws TourVisioAuthException login başarısız olursa
     */
    public synchronized String getToken() {
        if (cachedToken != null && !isExpired()) {
            log.debug("[TourVisioAuth] Cache'den geçerli token döndürülüyor.");
            return cachedToken;
        }
        log.info("[TourVisioAuth] Token yok veya süresi dolmuş — yeniden login yapılıyor.");
        return login();
    }

    /**
     * Cache'lenmiş token'ı siler; bir sonraki {@link #getToken()} çağrısında
     * yeniden login yapılmasını zorlar.
     *
     * <p>Kullanım: API'den 401 Unauthorized alındığında çağrılır.</p>
     */
    public synchronized void invalidateToken() {
        log.info("[TourVisioAuth] Token geçersiz kılındı — bir sonraki çağrıda yeniden login yapılacak.");
        cachedToken = null;
        tokenObtainedAt = 0;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Login
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * TourVisio login endpointine POST atar, dönen {@code body.token} değerini cache'ler.
     *
     * @return yeni token string'i
     * @throws TourVisioAuthException login başarısız olursa
     */
    private String buildUrl(String path) {
        String baseUrl = config.getBaseUrl();
        if (baseUrl == null) {
            baseUrl = "";
        }
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (baseUrl.endsWith("/api/") && path.startsWith("api/")) {
            path = path.substring(4);
        }
        return baseUrl + path;
    }

    private String login() {
        String url = buildUrl(LOGIN_PATH);

        log.info("[TourVisioAuth] TourVisio login isteği gönderiliyor: POST {}", url);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        TourVisioLoginRequest loginRequest = TourVisioLoginRequest.builder()
                .agency(config.getAgency())
                .user(config.getUsername())
                .password(config.getPassword())
                .build();

        HttpEntity<TourVisioLoginRequest> entity = new HttpEntity<>(loginRequest, headers);

        try {
            ResponseEntity<TourVisioLoginResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    TourVisioLoginResponse.class
            );

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new TourVisioAuthException(
                        "TourVisio login başarısız — HTTP status: " + response.getStatusCode());
            }

            String token = response.getBody().extractToken();
            if (token == null) {
                throw new TourVisioAuthException(
                        "TourVisio login yanıtı body.token içermiyor. " +
                        "Response body: " + response.getBody());
            }

            cachedToken = token;
            tokenObtainedAt = Instant.now().getEpochSecond();
            log.info("[TourVisioAuth] Login başarılı — token cache'lendi (TTL={}s).", TOKEN_TTL_SECONDS);

            return cachedToken;

        } catch (TourVisioAuthException e) {
            throw e;
        } catch (HttpClientErrorException e) {
            throw new TourVisioAuthException(
                    "TourVisio login 4xx hatası (status=" + e.getStatusCode() +
                    "): " + e.getResponseBodyAsString(), e);
        } catch (HttpServerErrorException e) {
            throw new TourVisioAuthException(
                    "TourVisio login 5xx hatası (status=" + e.getStatusCode() +
                    "): " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new TourVisioAuthException(
                    "TourVisio login sırasında beklenmedik hata: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private boolean isExpired() {
        return (Instant.now().getEpochSecond() - tokenObtainedAt) > TOKEN_TTL_SECONDS;
    }
}
