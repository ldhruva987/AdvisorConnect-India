package com.advisorconnect.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Populates Spring Security's {@code SecurityContext} from the identity headers the API
 * gateway injects ({@value SecurityHeaders#X_USER_ID} / {@value SecurityHeaders#X_USER_ROLE}),
 * so that downstream {@code .authenticated()} rules and {@code @PreAuthorize} annotations
 * have something real to authorise against.
 *
 * <p><strong>Fails closed.</strong> If either header is absent, blank, or the user id is not a
 * well-formed UUID, no {@code Authentication} is set and the chain simply continues — the
 * request then hits the service's authorisation rules unauthenticated and is rejected with
 * 401/403. The filter deliberately never throws for bad input, since an exception here would
 * surface as a 500 and mask an authentication failure as a server error.
 *
 * <p><strong>Not a {@code @Component} by design.</strong> Each service's component scan is
 * rooted at its own base package and would not discover {@code com.advisorconnect.common}.
 * Register it explicitly from the service's own {@code SecurityConfig}:
 *
 * <pre>{@code
 * @Bean
 * HeaderAuthenticationFilter headerAuthenticationFilter() {
 *     return new HeaderAuthenticationFilter();
 * }
 *
 * // ... and in the SecurityFilterChain:
 * http.addFilterBefore(headerAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);
 * }</pre>
 */
public class HeaderAuthenticationFilter extends OncePerRequestFilter {

    /**
     * Prefix Spring Security's {@code hasRole(...)} expects on a granted authority; the role
     * arrives on the wire unprefixed (e.g. {@code ADMIN}) and is stored as {@code ROLE_ADMIN}.
     */
    private static final String ROLE_PREFIX = "ROLE_";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String userId = request.getHeader(SecurityHeaders.X_USER_ID);
        String role = request.getHeader(SecurityHeaders.X_USER_ROLE);

        if (isPresent(userId) && isPresent(role) && isUuid(userId)) {
            var authentication = new UsernamePasswordAuthenticationToken(
                    userId,
                    null,
                    List.of(new SimpleGrantedAuthority(ROLE_PREFIX + role.trim())));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        // Always continue the chain — an unauthenticated request is rejected downstream by the
        // service's authorisation rules, not by short-circuiting here.
        filterChain.doFilter(request, response);
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean isUuid(String value) {
        try {
            UUID.fromString(value.trim());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
