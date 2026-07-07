package com.santsg.tourvisio.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * OpenAI Chat Completions API'sine bağlanan {@link AIProviderClient} implementasyonu.
 *
 * <p><strong>Güvenlik notu:</strong> API anahtarı yalnızca bu sınıfta okunur ve
 * hiçbir zaman response body'sine ya da loglarına yazılmaz.
 * Key kaynağı: {@code AI_API_KEY} environment variable veya
 * {@code application.properties} içindeki {@code ai.openai.api-key} property'si.</p>
 *
 * <p>Key tanımlanmamışsa servis "mock" modda çalışır –
 * gerçek bir HTTP isteği atılmaz, sabit bir cevap döner.
 * Bu sayede geliştirme ortamında key olmadan uygulama ayağa kalkar.</p>
 */
@Component
public class OpenAIClient implements AIProviderClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAIClient.class);

    private static final String OPENAI_CHAT_URL = "https://api.openai.com/v1/chat/completions";
    private static final String DEFAULT_MODEL    = "gpt-4o-mini";

    /** Environment variable: AI_API_KEY  veya  application.properties: ai.openai.api-key */
    @Value("${ai.openai.api-key:}")
    private String apiKey;

    @Value("${ai.openai.model:" + DEFAULT_MODEL + "}")
    private String model;

    private final RestTemplate restTemplate;

    public OpenAIClient(RestTemplateBuilder builder) {
        this.restTemplate = builder.build();
    }

    // -------------------------------------------------------------------------
    // AIProviderClient
    // -------------------------------------------------------------------------

    @Override
    public String complete(String prompt) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[OpenAIClient] API key bulunamadı – MOCK mod aktif. "
                    + "Gerçek yanıt almak için AI_API_KEY env variable'ını veya "
                    + "ai.openai.api-key property'sini tanımlayın.");
            return mockResponse(prompt);
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey); // key loglara düşmez

            Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(
                    Map.of("role", "system", "content", systemPrompt()),
                    Map.of("role", "user",   "content", prompt)
                ),
                "temperature", 0.3,
                "max_tokens", 512
            );

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                OPENAI_CHAT_URL, HttpMethod.POST, entity, Map.class
            );

            return extractContent(response.getBody());

        } catch (RestClientException ex) {
            log.error("[OpenAIClient] OpenAI API isteği başarısız: {}", ex.getMessage());
            return "AI servisine bağlanılamadı. Lütfen daha sonra tekrar deneyin.";
        }
    }

    @Override
    public String providerName() {
        return "openai";
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** OpenAI API yanıtından content metnini çıkarır. */
    @SuppressWarnings("unchecked")
    private String extractContent(Map<?, ?> body) {
        if (body == null) return "";
        try {
            List<?> choices = (List<?>) body.get("choices");
            if (choices == null || choices.isEmpty()) return "";
            Map<?, ?> choice  = (Map<?, ?>) choices.get(0);
            Map<?, ?> message = (Map<?, ?>) choice.get("message");
            return message != null ? (String) message.get("content") : "";
        } catch (ClassCastException e) {
            log.error("[OpenAIClient] Yanıt parse edilemedi", e);
            return "";
        }
    }

    /** Sistem promptu: AI'ya rolünü ve kurallarını tanımlar. */
    private String systemPrompt() {
        return """
                Sen TourVisio adlı bir seyahat asistanısın.
                Yalnızca otel arama, uçak bileti arama ve rezervasyon konularında yardım edebilirsin.
                Kısa, net ve Türkçe cevaplar ver.
                Asla API anahtarı, şifre veya gizli bilgi paylaşma.
                """;
    }

    /**
     * Key olmadan geliştirme ortamında kullanılan sahte cevap.
     * Production'da bu metoda ulaşılmamalıdır.
     */
    private String mockResponse(String prompt) {
        return "[MOCK] OpenAI API key tanımlanmamış. "
             + "Sorgunuz alındı ancak gerçek AI yanıtı üretilemedi. "
             + "Prompt önizlemesi: " + prompt.substring(0, Math.min(prompt.length(), 80)) + "...";
    }
}
