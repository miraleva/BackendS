package com.santsg.tourvisio.client;

import com.santsg.tourvisio.client.dto.GeminiGenerateContentRequest;
import com.santsg.tourvisio.client.dto.GeminiGenerateContentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Gemini Lite API'sine bağlanan {@link AIProviderClient} implementasyonu.
 *
 * <p><strong>Güvenlik notu:</strong> API anahtarı yalnızca bu sınıfta okunur ve
 * hiçbir zaman response body'sine ya da loglarına yazılmaz.
 * Key kaynağı: {@code GEMINI_LITE_API_KEY} environment variable veya
 * {@code application.properties} içindeki {@code gemini.lite.api-key} property'si.</p>
 */
@Component
public class GeminiExtractionClient implements AIProviderClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiExtractionClient.class);

    @Value("${gemini.lite.api-key:}")
    private String apiKey;

    @Value("${gemini.lite.api-url:}")
    private String apiUrl;

    private final RestTemplate restTemplate;

    @org.springframework.beans.factory.annotation.Autowired
    public GeminiExtractionClient(RestTemplateBuilder builder) {
        this(builder.build());
    }

    GeminiExtractionClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public String complete(String prompt) {
        return generate(prompt);
    }

    @Override
    public String providerName() {
        return "gemini-lite";
    }

    public String generate(String prompt) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[GeminiExtractionClient] API key not configured. Returning mock response.");
            return "[MOCK] Gemini Lite API key is not configured. Prompt received: " + prompt;
        }

        if (apiUrl == null || apiUrl.isBlank()) {
            log.warn("[GeminiExtractionClient] API URL not configured. Returning mock response.");
            return "[MOCK] Gemini Lite API URL is not configured. Prompt received: " + prompt;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-goog-api-key", apiKey);

            GeminiGenerateContentRequest requestBody = new GeminiGenerateContentRequest(prompt);
            HttpEntity<GeminiGenerateContentRequest> entity = new HttpEntity<>(requestBody, headers);

            GeminiGenerateContentResponse response = restTemplate.exchange(
                    apiUrl,
                    HttpMethod.POST,
                    entity,
                    GeminiGenerateContentResponse.class).getBody();

            return extractText(response);
        } catch (RestClientException ex) {
            log.error("[GeminiExtractionClient] Gemini API request failed: {}", ex.getMessage());
            return "[MOCK] Gemini API request failed: " + ex.getMessage();
        }
    }

    private String extractText(GeminiGenerateContentResponse response) {
        if (response == null || response.getCandidates() == null || response.getCandidates().isEmpty()) {
            return "";
        }

        GeminiGenerateContentResponse.Candidate candidate = response.getCandidates().get(0);
        if (candidate == null || candidate.getContent() == null || candidate.getContent().getParts() == null
                || candidate.getContent().getParts().isEmpty()) {
            return "";
        }

        return candidate.getContent().getParts().stream()
                .filter(part -> part != null && part.getText() != null)
                .map(GeminiGenerateContentResponse.Part::getText)
                .reduce("", (left, right) -> left + right);
    }
}
