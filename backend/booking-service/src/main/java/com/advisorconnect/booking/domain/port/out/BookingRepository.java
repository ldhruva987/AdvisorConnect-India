package com.advisorconnect.booking.domain.port.out;

import com.advisorconnect.booking.domain.model.Booking;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository {
    Booking save(Booking booking);
    Optional<Booking> findById(UUID id);
    List<Booking> findByAdvisorIdAndSessionDateTimeBetween(UUID advisorId, Instant from, Instant to);
    List<Booking> findByUserId(UUID userId);
}
