package com.santsg.tourvisio.config;

import com.santsg.tourvisio.client.TourVisioAuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.support.HttpRequestWrapper;

import java.io.IOException;

@Slf4j
public class TourVisioAuthInterceptor implements ClientHttpRequestInterceptor {

    private final TourVisioAuthService authService;

    public TourVisioAuthInterceptor(TourVisioAuthService authService) {
        this.authService = authService;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        // Skip modifying headers if it is the login endpoint
        if (request.getURI().getPath().contains("/authenticationservice/login")) {
            return execution.execute(request, body);
        }

        // Get a fresh/cached token
        String token = authService.getToken();

        // Wrap the request to allow modifying the authorization header safely
        HttpRequest wrapper = new HttpRequestWrapper(request) {
            private final HttpHeaders headers = new HttpHeaders();
            {
                headers.putAll(request.getHeaders());
            }
            @Override
            public HttpHeaders getHeaders() {
                return headers;
            }
        };
        wrapper.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer " + token);

        // Execute request
        ClientHttpResponse response = execution.execute(wrapper, body);

        // Auto-relogin if response status is 401 Unauthorized
        if (response.getStatusCode() == HttpStatus.UNAUTHORIZED) {
            log.warn("[TourVisioAuthInterceptor] TourVisio API returned 401 Unauthorized. Invalidating token and retrying request...");
            
            // Invalidate the existing token
            authService.invalidateToken();
            
            // Fetch a fresh token
            String newToken = authService.getToken();
            
            // Update the authorization header in the request wrapper
            wrapper.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer " + newToken);
            
            // Retry request execution
            return execution.execute(wrapper, body);
        }

        return response;
    }
}
