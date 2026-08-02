package com.advisorconnect.advisor.domain.port.in;

import com.advisorconnect.advisor.adapter.in.web.dto.ReviewDto;
import com.advisorconnect.advisor.adapter.in.web.dto.SubmitReviewRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ReviewUseCase {

    /**
     * Records a review and folds it into the advisor's running average, in one transaction.
     *
     * @throws com.advisorconnect.advisor.domain.model.ReviewNotAllowedException
     *         if the reviewer has no completed, unreviewed session with this advisor.
     */
    ReviewDto submitReview(UUID advisorId, UUID reviewerId, SubmitReviewRequest request);

    /** Public listing for an advisor's profile page, newest first. */
    Page<ReviewDto> getReviews(UUID advisorId, Pageable pageable);
}
