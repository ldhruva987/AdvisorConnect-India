package com.advisorconnect.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;

/**
 * Validation-only wrapper around jjwt, for services that must verify an access token
 * themselves rather than trusting gateway-injected headers — notably chat-service's
 * WebSocket handshake, which does not pass through the gateway's HTTP filter chain.
 *
 * <p><strong>Validation only.</strong> Token <em>issuance</em> stays exclusive to
 * auth-service's {@code JwtTokenProvider}; deliberately not duplicated here so there remains
 * exactly one place that can mint credentials. The key derivation and parser configuration
 * mirror that class exactly (HMAC-SHA over the raw secret bytes, jjwt 0.12.x parser API) so
 * tokens issued there verify here.
 *
 * <p>Expected claim shape, as produced by auth-service: {@code sub} = user id string,
 * {@code role} = role string, plus {@code iat}/{@code exp}. No {@code iss}, {@code aud} or
 * {@code jti} is issued, so none is required.
 *
 * <p><strong>Not a {@code @Component} by design</strong> — each service's component scan is
 * rooted at its own base package. Register it from the service's own configuration:
 *
 * <pre>{@code
 * @Bean
 * JwtValidator jwtValidator(@Value("${jwt.secret}") String secret) {
 *     return new JwtValidator(secret);
 * }
 * }</pre>
 */
public class JwtValidator {

    /** Claim carrying the caller's role, as written by auth-service's token provider. */
    public static final String ROLE_CLAIM = "role";

    private final SecretKey secretKey;

    public JwtValidator(String secret) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes());
    }

    /**
     * Verifies the token's signature and expiry and returns its claims.
     *
     * @param token a compact-serialised signed JWT, without any {@code Bearer } prefix
     * @return the verified payload
     * @throws JwtException             if the signature does not match, the token has expired,
     *                                  or it is not a well-formed signed JWT
     * @throws IllegalArgumentException if the token is null or blank
     */
    public Claims validateAndGetClaims(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Non-throwing variant of {@link #validateAndGetClaims(String)}.
     *
     * @return {@code true} if the token verifies and has not expired
     */
    public boolean isValid(String token) {
        try {
            validateAndGetClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * @return the {@code sub} claim (the caller's user id) of a verified token
     * @throws JwtException if the token does not verify
     */
    public String extractUserId(String token) throws JwtException {
        return validateAndGetClaims(token).getSubject();
    }

    /**
     * @return the {@code role} claim of a verified token, or {@code null} if absent
     * @throws JwtException if the token does not verify
     */
    public String extractRole(String token) throws JwtException {
        return validateAndGetClaims(token).get(ROLE_CLAIM, String.class);
    }
}
