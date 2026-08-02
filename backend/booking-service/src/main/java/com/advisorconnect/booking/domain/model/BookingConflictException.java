package com.advisorconnect.booking.domain.model;

/**
 * The requested slot overlaps a booking the advisor already holds (any status except
 * {@code CANCELLED}/{@code FAILED} — see {@code BookingService#holdsTheSlot}).
 *
 * <p>A distinct type rather than {@code IllegalStateException} so the web layer can answer 409:
 * this is a legitimate client-side conflict (the calendar moved between the client reading
 * availability and submitting the booking), not a server fault.
 */
public class BookingConflictException extends RuntimeException {

    public BookingConflictException(String message) {
        super(message);
    }
}
