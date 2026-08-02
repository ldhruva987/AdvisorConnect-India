package com.advisorconnect.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtValidatorTest {

    /** Must be >= 256 bits for HS256, matching the constraint auth-service's secret satisfies. */
    private static final String SECRET =
            "advisorconnect-test-signing-secret-value-0123456789";
    private static final String OTHER_SECRET =
            "a-completely-different-signing-secret-value-9876543210";

    private final JwtValidator validator = new JwtValidator(SECRET);

    /**
     * Mints a token exactly the way auth-service's {@code JwtTokenProvider} does:
     * subject = user id, {@code role} claim, {@code iat}, {@code exp}, HMAC signature.
     */
    private static String issueToken(String secret, String userId, String role, long ttlMs) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes());
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(userId)
                .claim("role", role)
                .issuedAt(new Date(now))
                .expiration(new Date(now + ttlMs))
                .signWith(key)
                .compact();
    }

    @Test
    @DisplayName("token signed with the validator's own secret parses and exposes its claims")
    void validatesTokenSignedWithSameSecret() {
        String userId = UUID.randomUUID().toString();
        String token = issueToken(SECRET, userId, "ADVISOR", 60_000);

        Claims claims = validator.validateAndGetClaims(token);

        assertThat(claims.getSubject()).isEqualTo(userId);
        assertThat(claims.get("role", String.class)).isEqualTo("ADVISOR");
        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration()).isAfter(new Date());
    }

    @Test
    @DisplayName("convenience accessors return the subject and role of a valid token")
    void exposesUserIdAndRole() {
        String userId = UUID.randomUUID().toString();
        String token = issueToken(SECRET, userId, "ADMIN", 60_000);

        assertThat(validator.extractUserId(token)).isEqualTo(userId);
        assertThat(validator.extractRole(token)).isEqualTo("ADMIN");
        assertThat(validator.isValid(token)).isTrue();
    }

    @Test
    @DisplayName("token signed with a different secret is rejected")
    void rejectsTokenSignedWithDifferentSecret() {
        String token = issueToken(OTHER_SECRET, UUID.randomUUID().toString(), "USER", 60_000);

        assertThatThrownBy(() -> validator.validateAndGetClaims(token))
                .isInstanceOf(SignatureException.class);

        assertThat(validator.isValid(token)).isFalse();
    }

    @Test
    @DisplayName("expired token is rejected")
    void rejectsExpiredToken() {
        // Issued and expired well in the past, beyond any default clock skew allowance.
        String token = issueToken(SECRET, UUID.randomUUID().toString(), "USER", -60_000);

        assertThatThrownBy(() -> validator.validateAndGetClaims(token))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("expired");

        assertThat(validator.isValid(token)).isFalse();
    }

    @Test
    @DisplayName("garbage string is rejected without leaking a non-JWT exception type")
    void rejectsMalformedToken() {
        assertThatThrownBy(() -> validator.validateAndGetClaims("this-is-not-a-jwt"))
                .isInstanceOf(JwtException.class);

        assertThat(validator.isValid("this-is-not-a-jwt")).isFalse();
    }

    @Test
    @DisplayName("token whose payload was tampered with after signing is rejected")
    void rejectsTamperedToken() {
        String token = issueToken(SECRET, UUID.randomUUID().toString(), "USER", 60_000);

        // Re-point the signature at a different, self-consistent header+payload: swapping the
        // signature of one valid token onto another must not verify.
        String otherToken = issueToken(SECRET, UUID.randomUUID().toString(), "ADMIN", 60_000);
        String[] parts = token.split("\\.");
        String[] otherParts = otherToken.split("\\.");
        String tampered = parts[0] + "." + parts[1] + "." + otherParts[2];

        assertThatThrownBy(() -> validator.validateAndGetClaims(tampered))
                .isInstanceOf(JwtException.class);

        assertThat(validator.isValid(tampered)).isFalse();
    }

    @Test
    @DisplayName("null and blank input are reported as invalid rather than NPE-ing")
    void rejectsNullAndBlankInput() {
        assertThat(validator.isValid(null)).isFalse();
        assertThat(validator.isValid("")).isFalse();
        assertThat(validator.isValid("   ")).isFalse();
    }

    @Test
    @DisplayName("tokens minted by one validator verify under another built from the same secret")
    void isInteroperableAcrossInstancesOfTheSameSecret() {
        String userId = UUID.randomUUID().toString();
        String token = issueToken(SECRET, userId, "USER", 60_000);

        JwtValidator otherInstance = new JwtValidator(SECRET);

        assertThat(otherInstance.extractUserId(token)).isEqualTo(userId);
    }
}
