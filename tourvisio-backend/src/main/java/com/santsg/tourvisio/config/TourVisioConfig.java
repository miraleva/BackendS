package com.santsg.tourvisio.config;

import com.santsg.tourvisio.client.TourVisioAuthService;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * TourVisio API yapılandırması.
 *
 * <p>Tüm değerler environment variable'lardan okunur
 * ({@code application.properties}'te {@code ${TOURVISIO_*}} placeholder'ları ile).
 * Hiçbir credential düz metin olarak properties dosyasına yazılmaz.</p>
 *
 * <h3>Gerekli env var'lar</h3>
 * <ul>
 *   <li>{@code TOURVISIO_BASE_URL} — ör. {@code https://test-service.tourvisio.com/v2}</li>
 *   <li>{@code TOURVISIO_AGENCY}   — TourVisio agency kodu</li>
 *   <li>{@code TOURVISIO_USER}     — Login kullanıcı adı</li>
 *   <li>{@code TOURVISIO_PASSWORD} — Login şifresi</li>
 *   <li>{@code TOURVISIO_MOCK_MODE} — {@code true} ise mock data kullanılır (varsayılan: true)</li>
 * </ul>
 */
@Configuration
@ConfigurationProperties(prefix = "tourvisio.api")
@Getter
@Setter
public class TourVisioConfig {

    /** TourVisio servis base URL'i (ör. https://test-service.tourvisio.com/v2) */
    private String baseUrl;

    /** TourVisio agency kodu */
    private String agency;

    /** Login kullanıcı adı */
    private String username;

    /** Login şifresi */
    private String password;

    /** true ise gerçek API'ye bağlanmaz, mock data döner */
    private boolean mockMode = true;

    /**
     * Gerçek TourVisio API'ye bağlanmak için gerekli bilgilerin
     * tamamının mevcut olup olmadığını kontrol eder.
     */
    public boolean isConfigured() {
        return !isBlank(baseUrl)
                && !isBlank(agency)
                && !isBlank(username)
                && !isBlank(password);
    }

    /**
     * TourVisio API çağrıları için kullanılacak RestTemplate.
     * Authorization header'ı dinamik olarak {@link TourVisioAuthService}
     * tarafından eklenir; burada sabit header konmaz.
     */
    @Bean("tourVisioRestTemplate")
    public RestTemplate tourVisioRestTemplate(@org.springframework.context.annotation.Lazy TourVisioAuthService authService) {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getInterceptors().add(new TourVisioAuthInterceptor(authService));
        return restTemplate;
    }

    // ─────────────────────────────────────────────────────────────────────────

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
