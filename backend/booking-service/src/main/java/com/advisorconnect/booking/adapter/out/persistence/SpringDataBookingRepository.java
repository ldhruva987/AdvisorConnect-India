package com.advisorconnect.booking.adapter.out.persistence;

import com.advisorconnect.booking.domain.model.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface SpringDataBookingRepository extends JpaRepository<Booking, UUID> {
    List<Booking> findByAdvisorIdAndSessionDateTimeBetween(UUID advisorId, Instant from, Instant to);
    List<Booking> findByUserId(UUID userId);
}
