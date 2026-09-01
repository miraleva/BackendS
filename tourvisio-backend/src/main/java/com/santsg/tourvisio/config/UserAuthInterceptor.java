package com.santsg.tourvisio.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class UserAuthInterceptor implements HandlerInterceptor {

    private final ActiveTokenRegistry tokenRegistry;
    private final TourVisioConfig tourVisioConfig;
    private final JwtProvider jwtProvider;

    @org.springframework.beans.factory.annotation.Value("${tourvisio.api.test-mode:false}")
    private boolean testMode;

    public UserAuthInterceptor(
            ActiveTokenRegistry tokenRegistry,
            TourVisioConfig tourVisioConfig,
            JwtProvider jwtProvider) {
        this.tokenRegistry = tokenRegistry;
        this.tourVisioConfig = tourVisioConfig;
        this.jwtProvider = jwtProvider;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) throws Exception {

        // =========================================================
        // TEST MODE
        // =========================================================
        if (testMode) {
            return true;
        }

        // =========================================================
        // CORS PREFLIGHT
        // =========================================================
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String requestURI = request.getRequestURI();

        // =========================================================
        // PUBLIC AUTH ENDPOINTS
        // Bu endpointlerde kullanıcı henüz giriş yapmadığı için
        // Bearer token aranmaz.
        // =========================================================
        if (requestURI.equals("/api/auth/login")
                || requestURI.equals("/api/auth/signup")
                || requestURI.equals("/api/auth/forgot-password")
                || requestURI.equals("/api/auth/reset-password")
                || requestURI.equals("/api/auth/admin-login")
                || requestURI.equals("/api/auth/oauth-login")
                || requestURI.equals("/api/auth/google-login")
                || requestURI.equals("/api/authenticationservice/login")) {
            return true;
        }

        // =========================================================
        // AUTHORIZATION HEADER
        // =========================================================
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authHeader != null
                && authHeader.startsWith("Bearer ")) {

            String token = authHeader.substring(7).trim();

            // =====================================================
            // 1. JWT
            // =====================================================
            try {

                com.auth0.jwt.interfaces.DecodedJWT jwt = jwtProvider.validateToken(token);

                Long userId = jwtProvider.getUserId(jwt);

                String email = jwtProvider.getEmail(jwt);

                if (userId != null) {
                    request.setAttribute(
                            "userId",
                            userId);
                }

                if (email != null && !email.isBlank()) {
                    request.setAttribute(
                            "email",
                            email);
                }

                return true;

            } catch (Exception ignored) {
                // JWT değilse registry kontrolüne geç.
            }

            // =====================================================
            // 2. ACTIVE TOKEN REGISTRY
            // =====================================================
            if (tokenRegistry.isValid(token)) {

                Long userId = tokenRegistry.getUserId(token);

                String email = tokenRegistry.getEmail(token);

                if (userId != null) {
                    request.setAttribute(
                            "userId",
                            userId);
                }

                if (email != null && !email.isBlank()) {
                    request.setAttribute(
                            "email",
                            email);
                }

                return true;
            }

            // =====================================================
            // 3. MOCK MODE
            // =====================================================
            if (tourVisioConfig.isMockMode()
                    && token.length() > 10) {
                return true;
            }
        }

        // =========================================================
        // GUEST ALLOWED ENDPOINTS CHECK
        // =========================================================
        boolean isGuestAllowed = isGuestAllowedEndpoint(requestURI, request.getMethod());
        if (isGuestAllowed) {
            return true;
        }

        // =========================================================
        // UNAUTHORIZED
        // =========================================================
        response.setHeader(
                "Access-Control-Allow-Origin",
                "*");

        response.setHeader(
                "Access-Control-Allow-Methods",
                "GET, POST, PUT, DELETE, OPTIONS");

        response.setHeader(
                "Access-Control-Allow-Headers",
                "*");

        response.setStatus(
                HttpStatus.UNAUTHORIZED.value());

        response.setContentType(
                "application/json");

        response.setCharacterEncoding(
                "UTF-8");

        response.getWriter().write(
                "{\"error\":\"Unauthorized\","
                        + "\"message\":\"Authentication is required. "
                        + "Please include a valid Bearer token "
                        + "in the Authorization header.\"}");

        return false;
    }

    private boolean isGuestAllowedEndpoint(String uri, String method) {
        if (uri.startsWith("/api/auth/")) return true;
        if (uri.startsWith("/api/hotels/") || uri.startsWith("/api/flights/")) return true;
        if (uri.startsWith("/api/reservations")) return true;
        if (uri.startsWith("/api/chat/")) {
            if (uri.endsWith("/claim")) return false;
            if ("DELETE".equalsIgnoreCase(method)) return false;
            return true;
        }
        return false;
    }
}