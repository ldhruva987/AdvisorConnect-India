package com.advisorconnect.booking.adapter.out.persistence;

import com.advisorconnect.booking.domain.model.Booking;
import com.advisorconnect.booking.domain.model.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataBookingRepository extends JpaRepository<Booking, UUID> {
    List<Booking> findByAdvisorIdAndSessionDateTimeBetween(UUID advisorId, Instant from, Instant to);
    List<Booking> findByUserId(UUID userId);
    Optional<Booking> findByStripePaymentIntentId(String stripePaymentIntentId);
    List<Booking> findByStatusAndSessionEndDateTimeBefore(BookingStatus status, Instant before);
}
