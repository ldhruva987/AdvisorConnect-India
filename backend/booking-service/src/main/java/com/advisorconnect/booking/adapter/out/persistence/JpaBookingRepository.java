package com.advisorconnect.booking.adapter.out.persistence;

import com.advisorconnect.booking.domain.model.Booking;
import com.advisorconnect.booking.domain.model.BookingStatus;
import com.advisorconnect.booking.domain.port.out.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaBookingRepository implements BookingRepository {

    private final SpringDataBookingRepository repo;

    @Override public Booking save(Booking booking) { return repo.save(booking); }
    @Override public Optional<Booking> findById(UUID id) { return repo.findById(id); }
    @Override public List<Booking> findByAdvisorIdAndSessionDateTimeBetween(UUID advisorId, Instant from, Instant to) {
        return repo.findByAdvisorIdAndSessionDateTimeBetween(advisorId, from, to);
    }
    @Override public List<Booking> findByUserId(UUID userId) { return repo.findByUserId(userId); }
    @Override public Optional<Booking> findByStripePaymentIntentId(String stripePaymentIntentId) {
        return repo.findByStripePaymentIntentId(stripePaymentIntentId);
    }
    @Override public List<Booking> findByStatusAndSessionEndDateTimeBefore(BookingStatus status, Instant before) {
        return repo.findByStatusAndSessionEndDateTimeBefore(status, before);
    }
}
