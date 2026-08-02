package com.advisorconnect.gateway.filter;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.util.List;

/**
 * Global JWT validation filter — Chain of Responsibility pattern.
 *
 * Behaviour:
 *  - Public routes pass through without JWT validation.
 *  - Valid JWT: extracts userId + role, injects X-User-Id and X-User-Role headers
 *    so downstream services trust these values without re-validating the token.
 *  - Invalid/missing JWT on protected paths: returns 401 immediately.
 *
 * Order -1 ensures this runs before all route filters.
 */
@Component
@Slf4j
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    /** A route that requires no authentication, identified by HTTP method <em>and</em> path. */
    private record PublicRoute(HttpMethod method, String pattern) {}

    /**
     * Public routes are matched on method as well as path. The previous implementation tested
     * {@code path.startsWith(...)} against a bare path list, so the entry {@code /api/advisors}
     * (intended to make only the public advisor list readable) also matched
     * {@code POST /api/advisors/apply} — an authenticated endpoint — and waved it straight
     * through with no token at all.
     */
    private static final List<PublicRoute> PUBLIC_ROUTES = List.of(
            new PublicRoute(HttpMethod.POST, "/api/auth/login"),
            new PublicRoute(HttpMethod.POST, "/api/auth/register"),
            new PublicRoute(HttpMethod.POST, "/api/auth/refresh"),
            new PublicRoute(HttpMethod.GET,  "/api/advisors"),
            new PublicRoute(HttpMethod.GET,  "/api/advisors/{username}"),
            // Stripe's webhook callback. Server-to-server, so there is never a JWT to check;
            // booking-service authenticates it by verifying the Stripe-Signature HMAC instead.
            // Without this entry the gateway would 401 every delivery and no booking would ever
            // leave PENDING.
            new PublicRoute(HttpMethod.POST, "/api/bookings/webhooks/stripe"),
            // The chat WebSocket handshake. The browser WebSocket constructor cannot set an
            // Authorization header, so the token travels as a ?token= query parameter instead
            // (see frontend buildChatSocketUrl and chat-service's JwtHandshakeInterceptor).
            // This filter only ever checks the Authorization header, so without this entry every
            // handshake was 401'd here before it reached chat-service's own independent JWT
            // verification — chat never connected through the gateway at all. chat-service is the
            // one that actually authenticates the caller for this route, exactly as booking-service
            // does for the Stripe webhook above.
            new PublicRoute(HttpMethod.GET,  "/ws/**")
    );

    /**
     * Never public, under any method. {@code AntPathMatcher} treats {@code {username}} as a
     * single-segment wildcard, so {@code GET /api/advisors/applications} would otherwise satisfy
     * the public profile-lookup pattern above and expose the admin application list. Matched as a
     * plain prefix rather than a segment-exact comparison so that nothing under this namespace —
     * {@code /applications/status}, {@code /applications/{id}/approve}, or anything added later —
     * can slip through. The cost is that a username beginning with "applications" cannot be
     * looked up anonymously; erring toward over-blocking is the right trade here.
     */
    private static final String ADVISOR_APPLICATIONS_PREFIX = "/api/advisors/applications";

    /** Actuator stays open under every method, as it was before. */
    private static final String ACTUATOR_PREFIX = "/actuator";

    private final SecretKey secretKey;

    public JwtAuthenticationFilter(@Value("${jwt.secret}") String secret) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes());
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        HttpMethod method = exchange.getRequest().getMethod();

        // Allow public endpoints without JWT
        if (isPublic(method, path)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7);
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String userId = claims.getSubject();
            String role   = claims.get("role", String.class);

            // Propagate verified user context downstream — downstream services
            // must trust these headers only when received from the gateway network.
            ServerWebExchange mutated = exchange.mutate()
                    .request(r -> r
                            .header("X-User-Id", userId)
                            .header("X-User-Role", role))
                    .build();

            return chain.filter(mutated);

        } catch (JwtException e) {
            log.debug("JWT validation failed for path={}: {}", path, e.getMessage());
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    /**
     * Fails closed: anything not positively identified as a public route requires a valid JWT.
     */
    boolean isPublic(HttpMethod method, String path) {
        if (path == null) {
            return false;
        }
        if (path.startsWith(ACTUATOR_PREFIX)) {
            return true;
        }
        // Checked before the pattern list so no public pattern can ever reach this namespace.
        if (path.startsWith(ADVISOR_APPLICATIONS_PREFIX)) {
            return false;
        }
        return PUBLIC_ROUTES.stream().anyMatch(route ->
                route.method().equals(method) && MATCHER.match(route.pattern(), path));
    }

    @Override
    public int getOrder() {
        return -1; // Highest priority — runs before all route-level filters
    }
}
