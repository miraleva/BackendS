package com.santsg.tourvisio.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for TourVisio integration.
 * Reads base URL and token from environment variables via application.properties.
 * Provides a RestTemplate bean that automatically adds the Authorization header.
 * The interceptor is a guard‑rail ensuring the token is never exposed in responses.
 */
@Configuration
public class TourVisioConfig {

    @Value("${tourvisio.api.base-url}")
    private String baseUrl;

    @Value("${tourvisio.api.token}")
    private String apiToken;

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getApiToken() {
        return apiToken;
    }

    /**
     * RestTemplate configured with an interceptor that adds the Authorization header.
     */
    @Bean
    public RestTemplate tourVisioRestTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>();
        interceptors.add((request, body, execution) -> {
            request.getHeaders().add("Authorization", "Bearer " + apiToken);
            request.getHeaders().add("Accept", "application/json");
            return execution.execute(request, body);
        });
        restTemplate.setInterceptors(interceptors);
        return restTemplate;
    }
}
