package com.advisorconnect.chat.infrastructure.websocket;

import com.advisorconnect.common.security.JwtValidator;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

import javax.crypto.SecretKey;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * The handshake is the only place chat-service authenticates anything, so these tests are the
 * load-bearing check that an unauthenticated socket can never be established.
 *
 * <p>Tokens are minted here with jjwt using the same key derivation as auth-service's
 * {@code JwtTokenProvider} and verified through a real {@link JwtValidator} — mocking the
 * validator would assert nothing about whether real tokens actually verify.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtHandshakeInterceptorTest {

    private static final String SECRET = "test-secret-key-that-is-long-enough-for-hmac-sha256!!";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes());

    private static final String SUBJECT = UUID.randomUUID().toString();

    @Mock
    private ServerHttpRequest request;

    @Mock
    private ServerHttpResponse response;

    @Mock
    private WebSocketHandler wsHandler;

    private JwtHandshakeInterceptor interceptor;
    private Map<String, Object> attributes;

    @BeforeEach
    void setUp() {
        interceptor = new JwtHandshakeInterceptor(new JwtValidator(SECRET));
        attributes = new HashMap<>();
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────

    private static String token(String subject, String role, Instant issuedAt, Instant expiry) {
        return Jwts.builder()
                .subject(subject)
                .claim("role", role)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiry))
                .signWith(KEY)
                .compact();
    }

    private static String validToken(String role) {
        Instant now = Instant.now();
        return token(SUBJECT, role, now, now.plus(Duration.ofMinutes(15)));
    }

    private boolean handshakeWithUri(String uri) {
        given(request.getURI()).willReturn(URI.create(uri));
        return interceptor.beforeHandshake(request, response, wsHandler, attributes);
    }

    private boolean handshakeWithToken(String token) {
        return handshakeWithUri("ws://chat-service:8085/ws/chat?advisorId="
                + UUID.randomUUID() + "&token=" + token);
    }

    // ── accepted handshakes ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("a token that verifies")
    class Accepted {

        @Test
        @DisplayName("is accepted and publishes the verified subject and role as attributes")
        void validTokenPopulatesAttributes() {
            boolean accepted = handshakeWithToken(validToken("USER"));

            assertThat(accepted).isTrue();
            assertThat(attributes)
                    .containsEntry(JwtHandshakeInterceptor.ATTR_USER_ID, SUBJECT)
                    .containsEntry(JwtHandshakeInterceptor.ATTR_ROLE, "USER");
            verify(response, never()).setStatusCode(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("carries the ADVISOR role through unaltered")
        void advisorRoleIsPreserved() {
            assertThat(handshakeWithToken(validToken("ADVISOR"))).isTrue();

            assertThat(attributes).containsEntry(JwtHandshakeInterceptor.ATTR_ROLE, "ADVISOR");
        }

        @Test
        @DisplayName("is found regardless of its position among the query parameters")
        void tokenParameterOrderDoesNotMatter() {
            boolean accepted = handshakeWithUri(
                    "ws://chat-service:8085/ws/chat?token=" + validToken("USER") + "&advisorId=" + UUID.randomUUID());

            assertThat(accepted).isTrue();
            assertThat(attributes).containsEntry(JwtHandshakeInterceptor.ATTR_USER_ID, SUBJECT);
        }
    }

    // ── rejected handshakes ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("a handshake is rejected with 401 and leaves no attributes when the token")
    class Rejected {

        @Test
        @DisplayName("is absent entirely")
        void missingToken() {
            assertThat(handshakeWithUri("ws://chat-service:8085/ws/chat?advisorId=" + UUID.randomUUID())).isFalse();
            assertRejected();
        }

        @Test
        @DisplayName("is present but empty")
        void blankToken() {
            assertThat(handshakeWithUri("ws://chat-service:8085/ws/chat?token=")).isFalse();
            assertRejected();
        }

        @Test
        @DisplayName("is not a JWT at all")
        void garbageToken() {
            assertThat(handshakeWithToken("not-a-jwt-at-all")).isFalse();
            assertRejected();
        }

        @Test
        @DisplayName("has expired")
        void expiredToken() {
            Instant longAgo = Instant.now().minus(Duration.ofHours(2));
            String expired = token(SUBJECT, "USER", longAgo, longAgo.plus(Duration.ofMinutes(15)));

            assertThat(handshakeWithToken(expired)).isFalse();
            assertRejected();
        }

        @Test
        @DisplayName("is signed with the wrong key")
        void wrongSignature() {
            SecretKey attackerKey = Keys.hmacShaKeyFor("a-completely-different-secret-of-sufficient-length".getBytes());
            String forged = Jwts.builder()
                    .subject(SUBJECT)
                    .claim("role", "ADVISOR")
                    .expiration(Date.from(Instant.now().plus(Duration.ofMinutes(15))))
                    .signWith(attackerKey)
                    .compact();

            assertThat(handshakeWithToken(forged)).isFalse();
            assertRejected();
        }

        @Test
        @DisplayName("is unsigned, so its claims are attacker-controlled")
        void unsignedToken() {
            String unsigned = Jwts.builder()
                    .subject(SUBJECT)
                    .claim("role", "ADMIN")
                    .compact();

            assertThat(handshakeWithToken(unsigned)).isFalse();
            assertRejected();
        }

        @Test
        @DisplayName("verifies but carries no role claim")
        void verifiedTokenWithoutRole() {
            Instant now = Instant.now();
            String roleless = Jwts.builder()
                    .subject(SUBJECT)
                    .issuedAt(Date.from(now))
                    .expiration(Date.from(now.plus(Duration.ofMinutes(15))))
                    .signWith(KEY)
                    .compact();

            assertThat(handshakeWithToken(roleless)).isFalse();
            assertRejected();
        }

        @Test
        @DisplayName("verifies but carries no subject")
        void verifiedTokenWithoutSubject() {
            Instant now = Instant.now();
            String subjectless = Jwts.builder()
                    .claim("role", "USER")
                    .issuedAt(Date.from(now))
                    .expiration(Date.from(now.plus(Duration.ofMinutes(15))))
                    .signWith(KEY)
                    .compact();

            assertThat(handshakeWithToken(subjectless)).isFalse();
            assertRejected();
        }

        private void assertRejected() {
            assertThat(attributes).isEmpty();
            verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        }
    }

    @Test
    @DisplayName("afterHandshake is a no-op and never throws, even for a failed handshake")
    void afterHandshakeIsSafe() {
        interceptor.afterHandshake(request, response, wsHandler, new IllegalStateException("boom"));
    }
}
