package com.advisorconnect.booking.adapter.in.web;

import com.advisorconnect.booking.adapter.in.web.dto.BookingResponse;
import com.advisorconnect.booking.adapter.in.web.dto.CreateBookingRequest;
import com.advisorconnect.booking.application.BookingService;
import com.advisorconnect.booking.domain.model.Booking;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/bookings")
@RequiredArgsConstructor
public class BookingController {

    /**
     * Role the gateway forwards for platform administrators. Compared against the
     * {@code X-User-Role} header; {@code @RequestHeader} needs compile-time constant names,
     * so the header names themselves stay as literals, matching {@code AdvisorController}.
     */
    private static final String ROLE_ADMIN = "ADMIN";

    private final BookingService bookingService;

    /**
     * Creates a booking in {@code PENDING} and returns it together with the Stripe client secret.
     *
     * <p>Returns {@link BookingResponse} rather than the bare {@code Booking} it used to: the
     * client secret is needed to actually complete the payment and is not a persisted field.
     * The booking becomes {@code CONFIRMED} only once Stripe's webhook reports the charge
     * succeeded.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public BookingResponse create(
            @Valid @RequestBody CreateBookingRequest req,
            @RequestHeader("X-User-Id") UUID userId) {
        return bookingService.createBooking(req, userId);
    }

    /** The caller's own bookings. Declared before {@code /{id}} for readability; Spring
     *  matches the literal path first regardless of declaration order. */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public List<Booking> myBookings(@RequestHeader("X-User-Id") UUID userId) {
        return bookingService.getMyBookings(userId);
    }

    /**
     * A booking by id, visible to the client who booked it, the advisor on the session, and
     * admins. Previously any authenticated caller could read any booking — including its
     * Stripe payment intent id.
     */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public Booking get(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID requesterId,
            @RequestHeader("X-User-Role") String role) {
        return bookingService.getBooking(id, requesterId, ROLE_ADMIN.equalsIgnoreCase(role));
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    public void cancel(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID userId) {
        bookingService.cancelBooking(id, userId);
    }

    @GetMapping("/availability/{advisorId}")
    public List<String> getAvailability(
            @PathVariable UUID advisorId,
            @RequestParam String date) {
        return bookingService.getAvailableSlots(advisorId, date);
    }
}
