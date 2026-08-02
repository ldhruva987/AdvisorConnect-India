package com.advisorconnect.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * Supplies the bucket key for the {@code RequestRateLimiter} filter on the user-service route.
 *
 * <p>Without a {@link KeyResolver} bean, Spring Cloud Gateway falls back to its auto-configured
 * {@code PrincipalNameKeyResolver}, which reads {@code exchange.getPrincipal()}. This gateway has
 * no Spring Security on the classpath, so nothing ever populates a principal and that resolver
 * yields an empty key on every request. {@code RequestRateLimiter} defaults {@code denyEmptyKey}
 * to {@code true} and answers an empty key with <strong>403 Forbidden</strong> — so the filter as
 * previously configured did not rate-limit {@code /api/users/**}, it rejected all of it. That went
 * unnoticed because the filter existed only in the local profile.
 *
 * <p>Keying instead on the verified user id that {@code JwtAuthenticationFilter} injects: that
 * filter is a {@code GlobalFilter} of order -1, so it runs ahead of the route-level filters and
 * {@code X-User-Id} is present on the exchange by the time the limiter reads it. The header is
 * trustworthy here specifically because it is the gateway's own output — the filter overwrites
 * any client-supplied value with the token's subject before forwarding.
 *
 * <p>Unauthenticated traffic (public routes) has no user id, so it falls back to the client
 * address. That is a coarser bucket — callers behind one NAT share it — but the alternative is
 * a single global bucket for every anonymous request, which one client could drain for everyone.
 */
@Configuration
public class RateLimiterConfig {

    /** Last-resort bucket, used only when a request has neither a user id nor a resolvable peer. */
    private static final String UNKNOWN_CLIENT_KEY = "unknown";

    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> Mono.just(resolveKey(exchange));
    }

    private static String resolveKey(ServerWebExchange exchange) {
        String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
        if (userId != null && !userId.isBlank()) {
            return "user:" + userId;
        }
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        if (remote != null && remote.getAddress() != null) {
            return "ip:" + remote.getAddress().getHostAddress();
        }
        return UNKNOWN_CLIENT_KEY;
    }
}
