package com.advisorconnect.advisor.adapter.out.persistence;

import com.advisorconnect.advisor.domain.model.Review;
import com.advisorconnect.advisor.domain.port.out.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaReviewRepository implements ReviewRepository {

    private final SpringDataReviewRepository repo;

    @Override public Review save(Review review) { return repo.save(review); }
    @Override public Page<Review> findByAdvisorId(UUID advisorId, Pageable pageable) { return repo.findByAdvisorId(advisorId, pageable); }
    @Override public boolean existsByBookingId(UUID bookingId) { return repo.existsByBookingId(bookingId); }
}
