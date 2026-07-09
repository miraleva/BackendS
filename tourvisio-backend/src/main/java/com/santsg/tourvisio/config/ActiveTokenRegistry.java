package com.santsg.tourvisio.config;

import org.springframework.stereotype.Component;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry that stores active Bearer tokens.
 * This is populated when a user successfully logs in via `/api/authenticationservice/login`.
 */
@Component
public class ActiveTokenRegistry {

    private final Set<String> activeTokens = ConcurrentHashMap.newKeySet();

    /**
     * Registers a new valid token.
     *
     * @param token The Bearer token (without prefix)
     */
    public void registerToken(String token) {
        if (token != null && !token.isBlank()) {
            activeTokens.add(token.trim());
        }
    }

    /**
     * Checks if the given token is active and valid.
     *
     * @param token The Bearer token to check
     * @return true if valid, false otherwise
     */
    public boolean isValid(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return activeTokens.contains(token.trim());
    }

    /**
     * Invalidates a token (e.g. on logout or expiration).
     *
     * @param token The token to invalidate
     */
    public void invalidateToken(String token) {
        if (token != null) {
            activeTokens.remove(token.trim());
        }
    }
}
