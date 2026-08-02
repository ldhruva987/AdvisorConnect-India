package com.advisorconnect.common.security;

/**
 * Names of the trusted identity headers the API gateway injects onto every request it
 * forwards downstream, after it has validated the caller's JWT.
 *
 * <p>These values must stay in lock-step with
 * {@code com.advisorconnect.gateway.filter.JwtAuthenticationFilter} (which writes them) and
 * with the {@code @RequestHeader} declarations in each service's controllers (which read
 * them). Header names are compared case-insensitively by the servlet container, but the
 * canonical casing is kept here so string literals never drift across modules.
 */
public final class SecurityHeaders {

    /** Subject of the validated JWT — the caller's user id, as a UUID string. */
    public static final String X_USER_ID = "X-User-Id";

    /** The {@code role} claim of the validated JWT, e.g. {@code USER}, {@code ADVISOR}, {@code ADMIN}. */
    public static final String X_USER_ROLE = "X-User-Role";

    private SecurityHeaders() {
        // constants holder — not instantiable
    }
}
