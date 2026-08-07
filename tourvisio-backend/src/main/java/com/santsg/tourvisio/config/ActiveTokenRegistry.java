package com.santsg.tourvisio.config;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry that stores active Bearer tokens
 * together with their user information.
 */
@Component
public class ActiveTokenRegistry {

    private final Set<String> activeTokens = ConcurrentHashMap.newKeySet();

    private final Map<String, Long> tokenUserIds = new ConcurrentHashMap<>();

    private final Map<String, String> tokenEmails = new ConcurrentHashMap<>();

    public Set<String> getActiveTokensSet() {
        return this.activeTokens;
    }

    public void restoreActiveTokensSet(Set<String> restoredTokens) {
        this.activeTokens.clear();

        if (restoredTokens != null) {
            this.activeTokens.addAll(restoredTokens);
        }
    }

    /**
     * Eski kullanım için bırakıldı.
     */
    public void registerToken(String token) {
        if (token != null && !token.isBlank()) {
            activeTokens.add(token.trim());
        }
    }

    /**
     * Kullanıcı bilgisiyle birlikte token kaydeder.
     */
    public void registerToken(
            String token,
            Long userId,
            String email) {

        if (token == null || token.isBlank()) {
            return;
        }

        String normalizedToken = token.trim();

        activeTokens.add(normalizedToken);

        if (userId != null) {
            tokenUserIds.put(
                    normalizedToken,
                    userId);
        }

        if (email != null && !email.isBlank()) {
            tokenEmails.put(
                    normalizedToken,
                    email);
        }
    }

    public boolean isValid(String token) {

        if (token == null || token.isBlank()) {
            return false;
        }

        return activeTokens.contains(token.trim());
    }

    public Long getUserId(String token) {

        if (token == null) {
            return null;
        }

        return tokenUserIds.get(token.trim());
    }

    public String getEmail(String token) {

        if (token == null) {
            return null;
        }

        return tokenEmails.get(token.trim());
    }

    public void invalidateToken(String token) {

        if (token == null) {
            return;
        }

        String normalizedToken = token.trim();

        activeTokens.remove(normalizedToken);
        tokenUserIds.remove(normalizedToken);
        tokenEmails.remove(normalizedToken);
    }
}