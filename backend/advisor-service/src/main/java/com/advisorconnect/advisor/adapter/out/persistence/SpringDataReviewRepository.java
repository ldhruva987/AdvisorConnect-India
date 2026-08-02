package com.advisorconnect.advisor.adapter.out.persistence;

import com.advisorconnect.advisor.domain.model.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SpringDataReviewRepository extends JpaRepository<Review, UUID> {

    Page<Review> findByAdvisorId(UUID advisorId, Pageable pageable);

    boolean existsByBookingId(UUID bookingId);
}
