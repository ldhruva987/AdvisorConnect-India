package com.advisorconnect.advisor.domain.port.out;

import com.advisorconnect.advisor.domain.model.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ReviewRepository {

    Review save(Review review);

    Page<Review> findByAdvisorId(UUID advisorId, Pageable pageable);

    boolean existsByBookingId(UUID bookingId);
}
