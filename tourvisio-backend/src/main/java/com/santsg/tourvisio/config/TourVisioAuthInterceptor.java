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
        long startTime = System.currentTimeMillis();
        ClientHttpResponse response = null;
        String errMsg = null;
        int statusCode = 200;
        String statusText = "OK";
        boolean success = true;
        String requestPayload = null;
        String responsePayload = null;

        try {
            if (body != null && body.length > 0) {
                requestPayload = new String(body, java.nio.charset.StandardCharsets.UTF_8);
            }

            // Skip modifying headers if it is the login endpoint
            if (request.getURI().getPath().contains("/authenticationservice/login")) {
                response = execution.execute(request, body);
                statusCode = response.getStatusCode().value();
                statusText = response.getStatusText();
                
                try (java.io.InputStream is = response.getBody()) {
                    responsePayload = org.springframework.util.StreamUtils.copyToString(is, java.nio.charset.StandardCharsets.UTF_8);
                } catch (Exception e) {
                    log.error("[TourVisioAuthInterceptor] Failed to read login response body: {}", e.getMessage());
                }

                if (response.getStatusCode().isError()) {
                    success = false;
                    errMsg = "HTTP " + statusCode + ": " + statusText;
                }
                return response;
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
            response = execution.execute(wrapper, body);
            statusCode = response.getStatusCode().value();
            statusText = response.getStatusText();

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
                response = execution.execute(wrapper, body);
                statusCode = response.getStatusCode().value();
                statusText = response.getStatusText();
            }

            // Read the final response body
            try (java.io.InputStream is = response.getBody()) {
                responsePayload = org.springframework.util.StreamUtils.copyToString(is, java.nio.charset.StandardCharsets.UTF_8);
            } catch (Exception e) {
                log.error("[TourVisioAuthInterceptor] Failed to read response body: {}", e.getMessage());
            }

            if (response.getStatusCode().isError()) {
                success = false;
                errMsg = "HTTP " + statusCode + ": " + statusText;
            }
            return response;

        } catch (Exception e) {
            success = false;
            errMsg = e.getMessage();
            statusCode = 500;
            statusText = "Internal Connection Error";
            if (e instanceof java.io.IOException) {
                throw (java.io.IOException) e;
            } else {
                throw new java.io.IOException(e);
            }
        } finally {
            long latency = System.currentTimeMillis() - startTime;
            TourVisioApiMonitor.logCall(
                request.getMethod().toString(),
                request.getURI().toString(),
                latency,
                statusCode,
                statusText,
                errMsg,
                success,
                requestPayload,
                responsePayload
            );
        }
    }
}
