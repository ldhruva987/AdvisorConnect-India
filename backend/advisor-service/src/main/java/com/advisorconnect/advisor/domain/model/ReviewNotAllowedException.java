package com.advisorconnect.advisor.domain.model;

/**
 * The caller is not entitled to leave this review — no completed session with the advisor, or
 * the only such session has already been reviewed.
 *
 * <p>A distinct type rather than {@code IllegalStateException} so the web layer can answer 409
 * without a blanket advice that would also reshape the responses of every pre-existing endpoint
 * in this service (none of which have an exception handler today, and changing that is not this
 * phase's business).
 */
public class ReviewNotAllowedException extends RuntimeException {

    public ReviewNotAllowedException(String message) {
        super(message);
    }
}
