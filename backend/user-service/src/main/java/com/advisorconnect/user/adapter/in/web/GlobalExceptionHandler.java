package com.advisorconnect.user.adapter.in.web;

import com.advisorconnect.user.application.UserProfileNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Keeps the 404 the controller used to produce by hand from
     * {@code ResponseEntity.notFound()}, now that "profile missing" is signalled as an exception
     * from the service layer instead of a null check in the web layer.
     */
    @ExceptionHandler(UserProfileNotFoundException.class)
    public ProblemDetail handleProfileNotFound(UserProfileNotFoundException ex) {
        var pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("Profile Not Found");
        return pd;
    }
}
