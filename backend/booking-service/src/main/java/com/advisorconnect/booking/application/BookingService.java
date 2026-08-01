package com.advisorconnect.booking.application;

import com.advisorconnect.booking.adapter.in.web.dto.CreateBookingRequest;
import com.advisorconnect.booking.domain.model.Booking;
import com.advisorconnect.booking.domain.model.BookingStatus;
import com.advisorconnect.booking.domain.port.out.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class BookingService {

    private final BookingRepository bookingRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public Booking createBooking(CreateBookingRequest req, UUID userId) {
        // Calculate price: $50/30min, $90/60min
        BigDecimal amount = req.getDurationMinutes() == 30
                ? new BigDecimal("50.00")
                : new BigDecimal("90.00");

        Booking booking = Booking.builder()
                .userId(userId)
                .advisorId(req.getAdvisorId())
                .sessionDateTime(req.getSessionDateTime())
                .durationMinutes(req.getDurationMinutes())
                .amountCharged(amount)
                .status(BookingStatus.CONFIRMED)
                // In production: process Stripe payment intent here
                .stripePaymentIntentId("pi_placeholder_" + UUID.randomUUID())
                .build();

        booking = bookingRepository.save(booking);

        kafkaTemplate.send("booking.created", booking.getId().toString(), Map.of(
                "eventType", "BOOKING_CREATED",
                "bookingId", booking.getId().toString(),
                "userId", userId.toString(),
                "advisorId", req.getAdvisorId().toString()
        ));

        log.info("Booking created: id={} userId={} advisorId={}", booking.getId(), userId, req.getAdvisorId());
        return booking;
    }

    @Transactional(readOnly = true)
    public Booking getBooking(UUID id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + id));
    }

    public void cancelBooking(UUID id, UUID userId) {
        Booking booking = getBooking(id);
        if (!booking.getUserId().equals(userId)) {
            throw new SecurityException("Not authorized to cancel this booking");
        }
        if (booking.getStatus() == BookingStatus.COMPLETED) {
            throw new IllegalStateException("Cannot cancel a completed booking");
        }
        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);

        kafkaTemplate.send("booking.cancelled", id.toString(), Map.of(
                "eventType", "BOOKING_CANCELLED",
                "bookingId", id.toString(),
                "userId", userId.toString()
        ));
    }

    @Transactional(readOnly = true)
    public List<String> getAvailableSlots(UUID advisorId, String date) {
        LocalDate localDate = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE);
        Instant from = localDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to   = localDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<Booking> existing = bookingRepository
                .findByAdvisorIdAndSessionDateTimeBetween(advisorId, from, to);

        Set<String> bookedSlots = new HashSet<>();
        for (Booking b : existing) {
            if (b.getStatus() != BookingStatus.CANCELLED) {
                bookedSlots.add(b.getSessionDateTime().toString());
            }
        }

        // Generate 9:00-17:00 slots in 30-min increments
        List<String> available = new ArrayList<>();
        LocalTime cursor = LocalTime.of(9, 0);
        LocalTime end    = LocalTime.of(17, 0);
        while (cursor.isBefore(end)) {
            Instant slot = localDate.atTime(cursor).toInstant(ZoneOffset.UTC);
            if (!bookedSlots.contains(slot.toString())) {
                available.add(slot.toString());
            }
            cursor = cursor.plusMinutes(30);
        }
        return available;
    }
}
