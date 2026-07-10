package com.santsg.tourvisio.config;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class JwtProvider {

    private final Algorithm algorithm;
    private final String issuer = "tourvisio";
    private final long expiryMs = 24 * 60 * 60 * 1000L; // 24 hours

    public JwtProvider(@Value("${jwt.secret:tourvisio-jwt-secret-key-1234567890-secure-random}") String secret) {
        this.algorithm = Algorithm.HMAC256(secret);
    }

    /**
     * Generates a JWT token for the given user ID and email.
     */
    public String generateToken(Long userId, String email) {
        return JWT.create()
                .withIssuer(issuer)
                .withSubject(email)
                .withClaim("userId", userId)
                .withClaim("email", email)
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + expiryMs))
                .sign(algorithm);
    }

    /**
     * Validates the JWT token and returns the decoded JWT.
     * Throws JWTVerificationException if the token is invalid or expired.
     */
    public DecodedJWT validateToken(String token) {
        JWTVerifier verifier = JWT.require(algorithm)
                .withIssuer(issuer)
                .build();
        return verifier.verify(token);
    }

    /**
     * Extracts the email claim from the decoded JWT.
     */
    public String getEmail(DecodedJWT jwt) {
        return jwt.getClaim("email").asString();
    }

    /**
     * Extracts the userId claim from the decoded JWT.
     */
    public Long getUserId(DecodedJWT jwt) {
        return jwt.getClaim("userId").asLong();
    }
}
