package com.santsg.tourvisio.config;

import com.santsg.tourvisio.client.TourVisioAuthService;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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
import javax.net.ssl.*;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.security.cert.X509Certificate;

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
    private boolean mockMode = false;

    /** Connection timeout in milliseconds (default: 15000 ms = 15s) */
    private int connectTimeout = 15000;

    /** Read timeout in milliseconds (default: 45000 ms = 45s) */
    private int readTimeout = 45000;

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
        // Bazi yogun rotalarda (ornek: Roma gibi cok sonuclu sehirler) TourVisio
        // birkac MB'lik cevaplari yavas gonderebiliyor; sinirsiz bekleme yerine
        // makul bir zaman asimi koyup arayan tarafin (FlightSearchService) daha
        // hizli "servis kullanilamiyor" ile geri donebilmesini sagliyoruz.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
                if (connection instanceof HttpsURLConnection httpsConnection) {
                    try {
                        SSLContext sslContext = SSLContext.getInstance("TLS");
                        sslContext.init(null, new TrustManager[]{new X509TrustManager() {
                            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                            public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                            public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                        }}, new java.security.SecureRandom());
                        httpsConnection.setSSLSocketFactory(sslContext.getSocketFactory());
                        httpsConnection.setHostnameVerifier((hostname, session) -> true);
                    } catch (Exception e) {
                        // ignore SSL error
                    }
                }
                super.prepareConnection(connection, httpMethod);
            }
        };
        factory.setConnectTimeout(connectTimeout > 0 ? connectTimeout : 15_000);
        factory.setReadTimeout(readTimeout > 0 ? readTimeout : 45_000);

        RestTemplate restTemplate = new RestTemplate(factory);
        restTemplate.getInterceptors().add(new TourVisioAuthInterceptor(authService));
        return restTemplate;
    }

    // ─────────────────────────────────────────────────────────────────────────

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
