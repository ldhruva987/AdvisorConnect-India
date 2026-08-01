package com.advisorconnect.booking.adapter.in.web;

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

    private final BookingService bookingService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public Booking create(
            @Valid @RequestBody CreateBookingRequest req,
            @RequestHeader("X-User-Id") UUID userId) {
        return bookingService.createBooking(req, userId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public Booking get(@PathVariable UUID id) {
        return bookingService.getBooking(id);
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
