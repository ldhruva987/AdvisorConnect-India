package com.advisorconnect.gateway.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the gateway's global JWT filter, driving it directly with a
 * {@link MockServerWebExchange} and a recording {@link GatewayFilterChain}. A {@code GlobalFilter}
 * is a plain function of (exchange, chain), so this covers the real decision logic without
 * standing up a reactive server or routing to live downstream services.
 */
class JwtAuthenticationFilterTest {

    private static final String SECRET = "test-secret-key-long-enough-for-hmac-sha256-signing-please";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes());

    private JwtAuthenticationFilter filter;
    private RecordingChain chain;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(SECRET);
        chain = new RecordingChain();
    }

    // ---------------------------------------------------------------- public routes

    @Test
    @DisplayName("GET /api/advisors (the bare public list) passes through without a token")
    void bareAdvisorListIsPublic() {
        var exchange = get("/api/advisors");

        filter.filter(exchange, chain).block();

        assertThat(chain.wasCalled()).isTrue();
        assertNoIdentityHeadersInjected();
    }

    @Test
    @DisplayName("GET /api/advisors?sector=CAREER keeps its public status with a query string")
    void advisorListWithQueryStringIsPublic() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/advisors?sector=CAREER").build());

        filter.filter(exchange, chain).block();

        assertThat(chain.wasCalled()).isTrue();
    }

    @Test
    @DisplayName("GET /api/advisors/{username} is public for a real username-shaped path")
    void advisorProfileByUsernameIsPublic() {
        var exchange = get("/api/advisors/somesername");

        filter.filter(exchange, chain).block();

        assertThat(chain.wasCalled()).isTrue();
    }

    @Test
    @DisplayName("POST /api/auth/login is public")
    void loginIsPublic() {
        var exchange = post("/api/auth/login");

        filter.filter(exchange, chain).block();

        assertThat(chain.wasCalled()).isTrue();
    }

    @Test
    @DisplayName("GET /ws/chat is public — the browser cannot set an Authorization header on a "
            + "WebSocket handshake, so chat-service verifies the ?token= param itself")
    void chatWebSocketHandshakeIsPublic() {
        var exchange = get("/ws/chat?token=whatever&advisorId=" + UUID.randomUUID());

        filter.filter(exchange, chain).block();

        assertThat(chain.wasCalled()).isTrue();
        assertNoIdentityHeadersInjected();
    }

    @Test
    @DisplayName("Actuator stays public under any method")
    void actuatorIsPublic() {
        for (ServerWebExchange exchange : new ServerWebExchange[]{
                get("/actuator/health"), get("/actuator/gateway/routes"), post("/actuator/refresh")}) {
            var freshChain = new RecordingChain();
            filter.filter(exchange, freshChain).block();
            assertThat(freshChain.wasCalled())
                    .as("actuator path %s", exchange.getRequest().getURI().getPath())
                    .isTrue();
        }
    }

    // ------------------------------------------------- regressions: must NOT be public

    @Nested
    @DisplayName("Regression: paths that must never be treated as public")
    class ProtectedPathRegressions {

        @Test
        @DisplayName("POST /api/advisors/apply requires auth — the startsWith(\"/api/advisors\") bug")
        void applyRequiresAuth() {
            var exchange = post("/api/advisors/apply");

            filter.filter(exchange, chain).block();

            assertRejected(exchange);
        }

        @Test
        @DisplayName("POST /api/advisors/apply is still rejected when the token is invalid")
        void applyWithGarbageTokenIsRejected() {
            var exchange = MockServerWebExchange.from(MockServerHttpRequest
                    .post("/api/advisors/apply")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-jwt")
                    .build());

            filter.filter(exchange, chain).block();

            assertRejected(exchange);
        }

        @Test
        @DisplayName("GET /api/advisors/applications must not match the public {username} pattern")
        void adminApplicationListRequiresAuth() {
            var exchange = get("/api/advisors/applications");

            filter.filter(exchange, chain).block();

            assertRejected(exchange);
        }

        @Test
        @DisplayName("GET /api/advisors/applications?status=PENDING also requires auth")
        void adminApplicationListWithFilterRequiresAuth() {
            var exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/advisors/applications?status=PENDING").build());

            filter.filter(exchange, chain).block();

            assertRejected(exchange);
        }

        @Test
        @DisplayName("Every route under /api/advisors/applications requires auth")
        void applicationSubResourcesRequireAuth() {
            for (ServerWebExchange exchange : new ServerWebExchange[]{
                    get("/api/advisors/applications/status"),
                    put("/api/advisors/applications/" + UUID.randomUUID() + "/approve"),
                    put("/api/advisors/applications/" + UUID.randomUUID() + "/reject")}) {
                var freshChain = new RecordingChain();
                filter.filter(exchange, freshChain).block();
                assertThat(freshChain.wasCalled())
                        .as("path %s must not be public", exchange.getRequest().getURI().getPath())
                        .isFalse();
                assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            }
        }

        @Test
        @DisplayName("Public routes are method-aware: POST /api/advisors is not the public GET list")
        void publicListIsGetOnly() {
            var exchange = post("/api/advisors");

            filter.filter(exchange, chain).block();

            assertRejected(exchange);
        }

        @Test
        @DisplayName("Public routes are method-aware: GET /api/auth/login is not the public POST")
        void loginIsPostOnly() {
            var exchange = get("/api/auth/login");

            filter.filter(exchange, chain).block();

            assertRejected(exchange);
        }

        @Test
        @DisplayName("{username} is a single segment — deeper advisor paths are not public")
        void deeperAdvisorPathsAreNotPublic() {
            var exchange = get("/api/advisors/someadvisor/reviews");

            filter.filter(exchange, chain).block();

            assertRejected(exchange);
        }

        @Test
        @DisplayName("An unrelated protected route with no token is rejected")
        void protectedRouteWithoutTokenIsRejected() {
            var exchange = get("/api/bookings/" + UUID.randomUUID());

            filter.filter(exchange, chain).block();

            assertRejected(exchange);
        }

        @Test
        @DisplayName("Public routes are method-aware: POST /ws/chat is not the public GET handshake")
        void wsRouteIsGetOnly() {
            var exchange = post("/ws/chat");

            filter.filter(exchange, chain).block();

            assertRejected(exchange);
        }
    }

    // ------------------------------------------------------------ header injection

    @Test
    @DisplayName("A valid JWT on a protected route injects X-User-Id and X-User-Role downstream")
    void validTokenInjectsIdentityHeaders() {
        String userId = UUID.randomUUID().toString();
        var exchange = MockServerWebExchange.from(MockServerHttpRequest
                .post("/api/advisors/apply")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(userId, "ADVISOR"))
                .build());

        filter.filter(exchange, chain).block();

        assertThat(chain.wasCalled()).isTrue();
        HttpHeaders forwarded = chain.captured.getRequest().getHeaders();
        assertThat(forwarded.getFirst("X-User-Id")).isEqualTo(userId);
        assertThat(forwarded.getFirst("X-User-Role")).isEqualTo("ADVISOR");
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("An expired JWT is rejected rather than passed through")
    void expiredTokenIsRejected() {
        String expired = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("role", "ADMIN")
                .issuedAt(new Date(System.currentTimeMillis() - 7_200_000))
                .expiration(new Date(System.currentTimeMillis() - 3_600_000))
                .signWith(KEY)
                .compact();
        var exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/advisors/applications")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expired)
                .build());

        filter.filter(exchange, chain).block();

        assertRejected(exchange);
    }

    @Test
    @DisplayName("A JWT signed with the wrong key is rejected")
    void wrongSignatureIsRejected() {
        SecretKey otherKey = Keys.hmacShaKeyFor(
                "a-completely-different-secret-key-of-sufficient-length!!".getBytes());
        String forged = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("role", "ADMIN")
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(otherKey)
                .compact();
        var exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/advisors/applications")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + forged)
                .build());

        filter.filter(exchange, chain).block();

        assertRejected(exchange);
    }

    @Test
    @DisplayName("Client-supplied identity headers are overwritten by the verified token's claims")
    void spoofedIdentityHeadersAreOverwritten() {
        String realUserId = UUID.randomUUID().toString();
        var exchange = MockServerWebExchange.from(MockServerHttpRequest
                .post("/api/advisors/apply")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(realUserId, "USER"))
                .header("X-User-Id", UUID.randomUUID().toString())
                .header("X-User-Role", "ADMIN")
                .build());

        filter.filter(exchange, chain).block();

        assertThat(chain.wasCalled()).isTrue();
        HttpHeaders forwarded = chain.captured.getRequest().getHeaders();
        assertThat(forwarded.get("X-User-Id")).containsExactly(realUserId);
        assertThat(forwarded.get("X-User-Role")).containsExactly("USER");
    }

    // ------------------------------------------------------------------- helpers

    private void assertRejected(ServerWebExchange exchange) {
        assertThat(chain.wasCalled())
                .as("request must not reach the downstream chain")
                .isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private void assertNoIdentityHeadersInjected() {
        HttpHeaders forwarded = chain.captured.getRequest().getHeaders();
        assertThat(forwarded.getFirst("X-User-Id")).isNull();
        assertThat(forwarded.getFirst("X-User-Role")).isNull();
    }

    private static String tokenFor(String userId, String role) {
        return Jwts.builder()
                .subject(userId)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 900_000))
                .signWith(KEY)
                .compact();
    }

    private static MockServerWebExchange get(String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
    }

    private static MockServerWebExchange post(String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.post(path).build());
    }

    private static MockServerWebExchange put(String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.put(path).build());
    }

    /** Captures the exchange handed downstream, so tests can assert pass-through and headers. */
    private static final class RecordingChain implements GatewayFilterChain {
        private ServerWebExchange captured;

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            this.captured = exchange;
            return Mono.empty();
        }

        boolean wasCalled() {
            return captured != null;
        }
    }
}
