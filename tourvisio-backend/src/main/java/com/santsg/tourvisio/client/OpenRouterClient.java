package com.santsg.tourvisio.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * OpenRouter (openrouter.ai) üzerinden çalışan {@link AIProviderClient} implementasyonu.
 *
 * <p>OpenRouter, tek bir API key ile onlarca farklı modele (OpenAI, Meta,
 * Google, vb. dahil) OpenAI uyumlu chat-completions formatıyla istek atmayı
 * sağlar. Bu sınıf tek bir sabit modele bağlanır — hangi model olduğu
 * (ör. "openai/gpt-oss-20b:free") kurucuya parametre olarak verilir, böylece
 * aynı sınıftan farklı modeller için birden fazla bean oluşturulabilir
 * (bkz. {@code AIProviderConfig}).</p>
 *
 * <p><strong>Güvenlik notu:</strong> API anahtarı yalnızca bu sınıfta okunur,
 * loglara veya response body'sine asla yazılmaz.</p>
 */
public class OpenRouterClient implements AIProviderClient {

    private static final Logger log = LoggerFactory.getLogger(OpenRouterClient.class);
    private static final String DEFAULT_API_URL = "https://openrouter.ai/api/v1/chat/completions";

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String apiUrl;
    private final String model;
    private final String providerLabel;

    public OpenRouterClient(RestTemplate restTemplate, String apiKey, String apiUrl, String model, String providerLabel) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
        this.apiUrl = (apiUrl == null || apiUrl.isBlank()) ? DEFAULT_API_URL : apiUrl;
        this.model = model;
        this.providerLabel = providerLabel;
    }

    @Override
    public String complete(String prompt) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[OpenRouterClient:{}] API key tanımlanmamış — MOCK mod.", providerLabel);
            return "[MOCK] OpenRouter API key tanımlanmamış (" + providerLabel + ").";
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            // OpenRouter, hangi uygulamanın istek attığını görebilmek için bu iki
            // header'ı önerir (zorunlu değil, atlanırsa istek yine çalışır).
            headers.set("HTTP-Referer", "https://tourvisio.local");
            headers.set("X-Title", "TourVisio Chatbot");

            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "user", "content", prompt)
                    ),
                    "temperature", 0.3,
                    "max_tokens", 700
            );

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    apiUrl, HttpMethod.POST, entity, Map.class
            );

            String content = extractContent(response.getBody());
            if (content == null || content.isBlank()) {
                log.warn("[OpenRouterClient:{}] Boş yanıt döndü, model: {}", providerLabel, model);
                return "[MOCK] OpenRouter (" + providerLabel + ") boş yanıt döndürdü.";
            }
            return content;

        } catch (RestClientException ex) {
            log.error("[OpenRouterClient:{}] İstek başarısız (model={}): {}", providerLabel, model, ex.getMessage());
            return "[MOCK] OpenRouter (" + providerLabel + ") isteği başarısız: " + ex.getMessage();
        }
    }

    @Override
    public String providerName() {
        return providerLabel;
    }

    @SuppressWarnings("unchecked")
    private String extractContent(Map<?, ?> body) {
        if (body == null) return "";
        try {
            List<?> choices = (List<?>) body.get("choices");
            if (choices == null || choices.isEmpty()) return "";
            Map<?, ?> choice = (Map<?, ?>) choices.get(0);
            Map<?, ?> message = (Map<?, ?>) choice.get("message");
            return message != null ? (String) message.get("content") : "";
        } catch (ClassCastException e) {
            log.error("[OpenRouterClient:{}] Yanıt parse edilemedi", providerLabel, e);
            return "";
        }
    }
}
