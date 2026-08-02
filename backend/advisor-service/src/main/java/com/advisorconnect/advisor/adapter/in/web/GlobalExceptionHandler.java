package com.advisorconnect.advisor.adapter.in.web;

import com.advisorconnect.advisor.domain.model.ReviewNotAllowedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Deliberately narrow. advisor-service has never had an exception advice, so every
 * {@code IllegalArgumentException}/{@code IllegalStateException} thrown by the pre-existing
 * application-review endpoints currently surfaces as a 500. Broadening this to catch those too
 * would quietly change the contract of endpoints outside this phase's scope — a worthwhile
 * cleanup, but not one to smuggle in under a reviews change.
 *
 * <p>So only the reviews path is mapped, via its own exception type: an ineligible or repeated
 * review is a legitimate client-side conflict and must not read as a server fault.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ReviewNotAllowedException.class)
    public ProblemDetail handleReviewNotAllowed(ReviewNotAllowedException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setTitle("Review Not Allowed");
        return pd;
    }
}
