package com.santsg.tourvisio.client;

import com.santsg.tourvisio.client.dto.GeminiGenerateContentRequest;
import com.santsg.tourvisio.client.dto.GeminiGenerateContentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class GeminiClient implements AIProviderClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.api.url:}")
    private String apiUrl;

    private final RestTemplate restTemplate;

    public GeminiClient(RestTemplateBuilder builder) {
        this(builder.build());
    }

    GeminiClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public String complete(String prompt) {
        return generate(prompt);
    }

    @Override
    public String providerName() {
        return "gemini";
    }

    public String generate(String prompt) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[GeminiClient] API key not configured. Returning mock response.");
            return "[MOCK] Gemini API key is not configured. Prompt received: " + prompt;
        }

        if (apiUrl == null || apiUrl.isBlank()) {
            log.warn("[GeminiClient] API URL not configured. Returning mock response.");
            return "[MOCK] Gemini API URL is not configured. Prompt received: " + prompt;
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
                    GeminiGenerateContentResponse.class
            ).getBody();

            return extractText(response);
        } catch (RestClientException ex) {
            log.error("[GeminiClient] Gemini API request failed: {}", ex.getMessage());
            return "Gemini service could not be reached. Please try again later.";
        }
    }

    private String extractText(GeminiGenerateContentResponse response) {
        if (response == null || response.getCandidates() == null || response.getCandidates().isEmpty()) {
            return "";
        }

        GeminiGenerateContentResponse.Candidate candidate = response.getCandidates().get(0);
        if (candidate == null || candidate.getContent() == null || candidate.getContent().getParts() == null || candidate.getContent().getParts().isEmpty()) {
            return "";
        }

        return candidate.getContent().getParts().stream()
                .filter(part -> part != null && part.getText() != null)
                .map(GeminiGenerateContentResponse.Part::getText)
                .reduce("", (left, right) -> left + right);
    }
}
