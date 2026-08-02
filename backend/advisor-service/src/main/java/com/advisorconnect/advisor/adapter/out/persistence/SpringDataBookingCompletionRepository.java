package com.advisorconnect.advisor.adapter.out.persistence;

import com.advisorconnect.advisor.domain.model.BookingCompletionRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SpringDataBookingCompletionRepository
        extends JpaRepository<BookingCompletionRecord, UUID> {

    Optional<BookingCompletionRecord> findFirstByUserIdAndAdvisorIdAndReviewedFalseOrderByCompletedAtAsc(
            UUID userId, UUID advisorId);
}
