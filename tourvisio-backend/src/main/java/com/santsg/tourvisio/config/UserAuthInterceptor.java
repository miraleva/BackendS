package com.santsg.tourvisio.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Intercepts incoming HTTP requests to verify the Bearer token in the Authorization header.
 */
@Component
public class UserAuthInterceptor implements HandlerInterceptor {

    private final ActiveTokenRegistry tokenRegistry;
    private final TourVisioConfig tourVisioConfig;

    @org.springframework.beans.factory.annotation.Value("${tourvisio.api.test-mode:false}")
    private boolean testMode;

    public UserAuthInterceptor(ActiveTokenRegistry tokenRegistry, TourVisioConfig tourVisioConfig) {
        this.tokenRegistry = tokenRegistry;
        this.tourVisioConfig = tourVisioConfig;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Bypass check if in test mode
        if (testMode) {
            return true;
        }

        // Handle OPTIONS request for CORS preflight
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // Get Authorization header
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            // In mock mode, we accept any dummy tokens or tokens starting with dummy values for development flexibility,
            // but we still enforce a non-empty token presence.
            if (tokenRegistry.isValid(token) || (tourVisioConfig.isMockMode() && token.length() > 10)) {
                return true;
            }
        }

        // Return 401 Unauthorized if token is missing or invalid
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\": \"Unauthorized\", \"message\": \"Authentication is required. Please include a valid Bearer token in the Authorization header.\"}");
        return false;
    }
}
