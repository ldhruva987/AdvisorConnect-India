package com.advisorconnect.booking.domain.port.out;

import com.advisorconnect.booking.domain.model.Booking;
import com.advisorconnect.booking.domain.model.BookingStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository {
    Booking save(Booking booking);
    Optional<Booking> findById(UUID id);
    List<Booking> findByAdvisorIdAndSessionDateTimeBetween(UUID advisorId, Instant from, Instant to);
    List<Booking> findByUserId(UUID userId);

    /**
     * Looks a booking up by its Stripe PaymentIntent id — the only handle a webhook carries.
     * Ids are unique per intent, and one intent backs exactly one booking.
     */
    Optional<Booking> findByStripePaymentIntentId(String stripePaymentIntentId);

    /**
     * Bookings in {@code status} whose session has already ended, used by the completion sweep.
     *
     * <p>Rows written before {@code sessionEndDateTime} existed carry null there and are simply
     * not returned — SQL comparisons against null are never true. Those are historical rows
     * predating this column, so leaving them alone is the right outcome.
     */
    List<Booking> findByStatusAndSessionEndDateTimeBefore(BookingStatus status, Instant before);
}
