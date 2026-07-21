package com.santsg.tourvisio.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.santsg.tourvisio.entity.User;
import com.santsg.tourvisio.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * Service for verifying Google OAuth2 ID tokens
 * and finding or creating users based on the verified identity.
 */
@Service
@Slf4j
public class OAuthService {

    private final UserRepository userRepository;

    @Value("${oauth.google.client-id:}")
    private String googleClientId;

    public OAuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // ──────────────────────────────────────────────────────────────────
    // Google Token Verification
    // ──────────────────────────────────────────────────────────────────

    /**
     * Verifies a Google ID token and returns the user's info.
     *
     * @param idToken the ID token from Google Sign-In
     * @return map with email, firstName, lastName
     * @throws Exception if verification fails
     */
    public Map<String, String> verifyGoogleToken(String idToken) throws Exception {
        if (googleClientId == null || googleClientId.isBlank()) {
            log.error("[OAuthService] Google Client ID is not configured! Check application.properties or GOOGLE_CLIENT_ID env var.");
            throw new IllegalStateException("Google Client ID is not configured on backend.");
        }

        log.info("[OAuthService] Verifying Google ID Token against configured googleClientId={}", googleClientId);

        if (idToken != null) {
            idToken = idToken.trim();
        }

        GoogleIdToken googleToken = null;
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(), GsonFactory.getDefaultInstance())
                    .setAudience(Collections.singletonList(googleClientId))
                    .setAcceptableTimeSkewSeconds(300)
                    .build();

            googleToken = verifier.verify(idToken);
        } catch (Exception e) {
            log.error("[OAuthService] Exception during Google ID Token verification: {}", e.getMessage(), e);
        }

        if (googleToken == null) {
            // Log unverified token details to help diagnose audience/issuer/expiration mismatch
            try {
                GoogleIdToken unverifiedToken = GoogleIdToken.parse(GsonFactory.getDefaultInstance(), idToken);
                GoogleIdToken.Payload payload = unverifiedToken.getPayload();
                log.warn("[OAuthService] Verification failed! Token payload -> aud={}, iss={}, sub={}, email={}, exp={}",
                        payload.getAudience(), payload.getIssuer(), payload.getSubject(), payload.getEmail(), payload.getExpirationTimeSeconds());

                if (!googleClientId.equals(payload.getAudience())) {
                    log.error("[OAuthService] Audience Mismatch! Configured backend googleClientId='{}' but Token audience='{}'",
                            googleClientId, payload.getAudience());
                }
            } catch (Exception parseEx) {
                log.warn("[OAuthService] Could not parse unverified token payload: {}", parseEx.getMessage());
            }

            throw new SecurityException("Invalid Google ID token verification failed");
        }

        GoogleIdToken.Payload payload = googleToken.getPayload();
        String email = payload.getEmail();
        String firstName = (String) payload.get("given_name");
        String lastName = (String) payload.get("family_name");

        if (firstName == null || firstName.isBlank()) firstName = "User";
        if (lastName == null || lastName.isBlank()) lastName = "-";

        if (firstName.length() > 40) firstName = firstName.substring(0, 40);
        if (lastName.length() > 40) lastName = lastName.substring(0, 40);

        log.info("[OAuthService] Google token verified successfully for email={}", email);

        return Map.of(
                "email", email,
                "firstName", firstName,
                "lastName", lastName
        );
    }

    // ──────────────────────────────────────────────────────────────────
    // Find or Create User
    // ──────────────────────────────────────────────────────────────────

    /**
     * Finds an existing user by email or creates a new one for OAuth login.
     * If user already exists (even with LOCAL provider), returns the existing user.
     *
     * @param email     user email
     * @param firstName first name
     * @param lastName  last name
     * @param provider  GOOGLE
     * @return the found or created User entity
     */
    public User findOrCreateUser(String email, String firstName, String lastName, String provider) {
        Optional<User> existingUser = userRepository.findByEmail(email);

        String safeProvider = (provider != null) ? provider.toUpperCase() : "GOOGLE";

        if (existingUser.isPresent()) {
            User user = existingUser.get();
            log.info("[OAuthService] Existing user found for email={}, provider={}", email, provider);
            if (user.getAuthProvider() == null) {
                user.setAuthProvider(safeProvider);
                return userRepository.save(user);
            }
            return user;
        }

        String safeFirstName = (firstName != null && !firstName.isBlank()) ? firstName : "User";
        String safeLastName = (lastName != null && !lastName.isBlank()) ? lastName : "-";

        if (safeFirstName.length() > 40) safeFirstName = safeFirstName.substring(0, 40);
        if (safeLastName.length() > 40) safeLastName = safeLastName.substring(0, 40);

        // Create new user for OAuth login
        // Assign a secure random hashed dummy password to support DB schemas with NOT NULL constraints
        String dummyPassword = org.mindrot.jbcrypt.BCrypt.hashpw(java.util.UUID.randomUUID().toString(), org.mindrot.jbcrypt.BCrypt.gensalt());

        User newUser = User.builder()
                .firstName(safeFirstName)
                .lastName(safeLastName)
                .email(email)
                .password(dummyPassword)
                .authProvider(safeProvider)
                .build();

        User savedUser = userRepository.save(newUser);
        log.info("[OAuthService] New OAuth user created: id={}, email={}, provider={}", savedUser.getId(), email, provider);

        return savedUser;
    }
}
