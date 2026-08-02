package com.advisorconnect.chat.infrastructure.websocket;

import com.advisorconnect.common.security.JwtValidator;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

/**
 * Authenticates the WebSocket handshake before it is upgraded.
 *
 * <p>The chat WebSocket does not traverse the gateway's HTTP filter chain, so without this
 * interceptor the handler would have no verified caller identity at all — it previously read
 * {@code ?userId=} straight off the query string, which any client could set to any value.
 *
 * <p>The token travels as a {@code ?token=} query parameter rather than an
 * {@code Authorization} header because the browser {@code WebSocket} constructor cannot set
 * custom headers. This is the standard accepted pattern for native WebSocket auth.
 *
 * <p>On success the verified subject and role are placed into the handshake {@code attributes}
 * map, which Spring copies into {@link org.springframework.web.socket.WebSocketSession#getAttributes()}.
 * Those two entries are the <em>only</em> trustworthy identity available to the handler.
 *
 * <p>On failure the handshake is rejected with {@code 401} and never upgrades, so the client
 * ends up with a failed HTTP request rather than an unauthenticated socket.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    /** Session attribute holding the verified {@code sub} claim — the caller's own user id. */
    public static final String ATTR_USER_ID = "userId";

    /** Session attribute holding the verified {@code role} claim ({@code USER}/{@code ADVISOR}/{@code ADMIN}). */
    public static final String ATTR_ROLE = "role";

    /** Query parameter carrying the compact-serialised access token. */
    public static final String TOKEN_PARAM = "token";

    private final JwtValidator jwtValidator;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {

        String token = extractToken(request.getURI());
        if (token == null || token.isBlank()) {
            log.debug("WS handshake rejected: no '{}' query parameter", TOKEN_PARAM);
            return reject(response);
        }

        Claims claims;
        try {
            claims = jwtValidator.validateAndGetClaims(token);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("WS handshake rejected: token did not verify ({})", e.getMessage());
            return reject(response);
        }

        String userId = claims.getSubject();
        String role = claims.get(JwtValidator.ROLE_CLAIM, String.class);
        if (userId == null || userId.isBlank() || role == null || role.isBlank()) {
            log.debug("WS handshake rejected: verified token is missing a subject or role claim");
            return reject(response);
        }

        attributes.put(ATTR_USER_ID, userId);
        attributes.put(ATTR_ROLE, role);
        log.debug("WS handshake accepted for userId={} role={}", userId, role);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        // Nothing to clean up: all state lives in the attributes map handed to beforeHandshake.
    }

    private static String extractToken(URI uri) {
        if (uri == null) {
            return null;
        }
        // UriComponentsBuilder handles percent-decoding and repeated/empty parameters, which
        // hand-rolled query splitting gets wrong for URL-safe base64 payloads.
        return UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst(TOKEN_PARAM);
    }

    private static boolean reject(ServerHttpResponse response) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return false;
    }
}
