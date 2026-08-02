package com.advisorconnect.advisor.adapter.out.persistence;

import com.advisorconnect.advisor.domain.model.BookingCompletionRecord;
import com.advisorconnect.advisor.domain.port.out.BookingCompletionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaBookingCompletionRepository implements BookingCompletionRepository {

    private final SpringDataBookingCompletionRepository repo;

    @Override public BookingCompletionRecord save(BookingCompletionRecord record) { return repo.save(record); }
    @Override public Optional<BookingCompletionRecord> findById(UUID bookingId) { return repo.findById(bookingId); }
    @Override public boolean existsById(UUID bookingId) { return repo.existsById(bookingId); }

    @Override
    public Optional<BookingCompletionRecord> findOldestUnreviewed(UUID userId, UUID advisorId) {
        return repo.findFirstByUserIdAndAdvisorIdAndReviewedFalseOrderByCompletedAtAsc(userId, advisorId);
    }
}
