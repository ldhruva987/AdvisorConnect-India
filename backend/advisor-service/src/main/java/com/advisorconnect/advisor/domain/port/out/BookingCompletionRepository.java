package com.advisorconnect.advisor.domain.port.out;

import com.advisorconnect.advisor.domain.model.BookingCompletionRecord;

import java.util.Optional;
import java.util.UUID;

public interface BookingCompletionRepository {

    BookingCompletionRecord save(BookingCompletionRecord record);

    Optional<BookingCompletionRecord> findById(UUID bookingId);

    boolean existsById(UUID bookingId);

    /**
     * The oldest completed-but-unreviewed session between this pair, if any — the booking a
     * review submission will be charged against.
     *
     * <p>Oldest first so that a client with several completed sessions spends them in the order
     * they happened, which keeps the mapping from review to session predictable.
     */
    Optional<BookingCompletionRecord> findOldestUnreviewed(UUID userId, UUID advisorId);
}
