package com.advisorconnect.booking.adapter.in.web;

import com.advisorconnect.booking.domain.model.BookingConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Deliberately narrow. booking-service has never had an exception advice, so every
 * {@code IllegalArgumentException}/{@code IllegalStateException} thrown by the pre-existing
 * endpoints currently surfaces as a 500. Broadening this to catch those too would quietly change
 * the contract of endpoints outside this fix's scope — a worthwhile cleanup, but not this one's.
 *
 * <p>So only the overlap check is mapped, via its own exception type: a double-booked slot is a
 * legitimate client-side conflict and must not read as a server fault.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BookingConflictException.class)
    public ProblemDetail handleBookingConflict(BookingConflictException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setTitle("Booking Conflict");
        return pd;
    }
}
