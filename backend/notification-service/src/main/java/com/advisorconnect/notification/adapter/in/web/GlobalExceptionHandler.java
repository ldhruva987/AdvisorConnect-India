package com.advisorconnect.notification.adapter.in.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps the service layer's two failure signals onto HTTP, in the {@code ProblemDetail} style
 * user-service and auth-service already use.
 *
 * <p>Without this, {@code NotificationService}'s ownership check would surface as a 500 — which
 * is what happens in booking-service today, and is the reason its integration test can only
 * assert "not 200" for an unauthorised read. A failed authorisation is a client error, and the
 * inbox is new enough to get it right from the start rather than inherit that.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Somebody else's notification. 403 rather than 404: the id in the URL came from somewhere,
     * and pretending the row does not exist would be a lie the client can disprove by watching
     * the timing. Existence of an opaque UUID is not a secret worth protecting here.
     */
    @ExceptionHandler(SecurityException.class)
    public ProblemDetail handleForbidden(SecurityException ex) {
        var pd = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        pd.setTitle("Forbidden");
        return pd;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleNotFound(IllegalArgumentException ex) {
        var pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("Not Found");
        return pd;
    }
}
