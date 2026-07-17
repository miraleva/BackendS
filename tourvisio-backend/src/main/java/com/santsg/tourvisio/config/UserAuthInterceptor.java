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
    private final JwtProvider jwtProvider;

    @org.springframework.beans.factory.annotation.Value("${tourvisio.api.test-mode:false}")
    private boolean testMode;

    public UserAuthInterceptor(ActiveTokenRegistry tokenRegistry, TourVisioConfig tourVisioConfig, JwtProvider jwtProvider) {
        this.tokenRegistry = tokenRegistry;
        this.tourVisioConfig = tourVisioConfig;
        this.jwtProvider = jwtProvider;
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

        // ─────────────────────────────────────────────────────────────────────────
        // YENİ EKLEDİĞİMİZ KISIM: Şifremi Unuttum ve Sıfırlama bypass kontrolleri
        // ─────────────────────────────────────────────────────────────────────────
        String requestURI = request.getRequestURI();
        if (requestURI.contains("/api/auth/forgot-password") || requestURI.contains("/api/auth/reset-password")) {
            return true; // Token aramadan bu endpoint'lerin geçmesine izin ver
        }
        // ─────────────────────────────────────────────────────────────────────────

        // Get Authorization header
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            
            // 1. Try to validate as our JWT
            try {
                com.auth0.jwt.interfaces.DecodedJWT jwt = jwtProvider.validateToken(token);
                Long userId = jwtProvider.getUserId(jwt);
                String email = jwtProvider.getEmail(jwt);
                request.setAttribute("userId", userId);
                request.setAttribute("email", email);
                return true;
            } catch (Exception e) {
                // Not a valid JWT or expired, fall back to existing token registry check
            }

            // 2. Fall back to existing token validation
            if (tokenRegistry.isValid(token) || (tourVisioConfig.isMockMode() && token.length() > 10)) {
                return true;
            }
        }

        // Return 401 Unauthorized if token is missing or invalid
        // Manually add CORS headers because short-circuiting MVC preHandle bypasses CorsFilter/registry
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "*");
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\": \"Unauthorized\", \"message\": \"Authentication is required. Please include a valid Bearer token in the Authorization header.\"}");
        return false;
    }
}