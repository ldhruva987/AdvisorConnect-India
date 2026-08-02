package com.advisorconnect.advisor.application;

import com.advisorconnect.advisor.adapter.in.web.dto.ReviewDto;
import com.advisorconnect.advisor.adapter.in.web.dto.SubmitReviewRequest;
import com.advisorconnect.advisor.domain.model.AdvisorProfile;
import com.advisorconnect.advisor.domain.model.BookingCompletionRecord;
import com.advisorconnect.advisor.domain.model.Review;
import com.advisorconnect.advisor.domain.model.ReviewNotAllowedException;
import com.advisorconnect.advisor.domain.port.in.ReviewUseCase;
import com.advisorconnect.advisor.domain.port.out.AdvisorProfileRepository;
import com.advisorconnect.advisor.domain.port.out.BookingCompletionRepository;
import com.advisorconnect.advisor.domain.port.out.ReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * Reviews and the advisor rating they feed.
 *
 * <p>This service is the reason reviews were placed in advisor-service rather than a service of
 * their own: accepting a review and moving {@code AdvisorProfile.averageRating} are one atomic
 * step here, against one database. Split across services it would have been a distributed
 * transaction or an eventually-consistent rating that disagrees with its own review list.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewService implements ReviewUseCase {

    /** Ratings are displayed to two decimals; the stored value matches what is shown. */
    private static final int RATING_SCALE = 2;

    private final ReviewRepository reviewRepository;
    private final BookingCompletionRepository bookingCompletionRepository;
    private final AdvisorProfileRepository profileRepository;

    /**
     * Validate eligibility, persist the review, spend the booking, and fold the rating into the
     * advisor's average — all or nothing.
     *
     * <p>The all-or-nothing part matters more than it looks. If the review row committed but the
     * profile update did not, the advisor's displayed rating would permanently disagree with the
     * reviews listed underneath it, with nothing in the system to reconcile them: there is no
     * recompute job, by design, because the incremental update below is supposed to be the only
     * writer.
     */
    @Override
    @Transactional
    public ReviewDto submitReview(UUID advisorId, UUID reviewerId, SubmitReviewRequest request) {
        int rating = validatedRating(request);

        // Eligibility is a completed session that has not been spent yet. Absent that, the caller
        // is either reviewing an advisor they never saw or reviewing the same session twice; both
        // land here, because a spent booking stops being an unreviewed one.
        BookingCompletionRecord record = bookingCompletionRepository
                .findOldestUnreviewed(reviewerId, advisorId)
                .orElseThrow(() -> new ReviewNotAllowedException(
                        "No completed, unreviewed session with this advisor"));

        // Belt and braces against the flag drifting from the review table — a redelivered
        // completion event that reset the row, a half-applied migration, a manual fix in prod.
        // The unique constraint on reviews.booking_id is the last line; this turns what would be
        // an opaque 500 into the 409 it actually is.
        if (reviewRepository.existsByBookingId(record.getBookingId())) {
            throw new ReviewNotAllowedException("This session has already been reviewed");
        }

        Review review = reviewRepository.save(Review.builder()
                .advisorId(advisorId)
                .userId(reviewerId)
                .bookingId(record.getBookingId())
                .rating(rating)
                .comment(normaliseComment(request.getComment()))
                .createdAt(Instant.now())
                .build());

        record.setReviewed(true);
        bookingCompletionRepository.save(record);

        applyRatingToProfile(advisorId, rating);

        log.info("Review accepted for advisorId={} by userId={} against bookingId={}",
                advisorId, reviewerId, record.getBookingId());
        return toDto(review);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReviewDto> getReviews(UUID advisorId, Pageable pageable) {
        return reviewRepository.findByAdvisorId(advisorId, pageable).map(ReviewService::toDto);
    }

    // ── Rating maths ────────────────────────────────────────────────────────────

    /**
     * Folds one new rating into the running mean:
     * {@code newAvg = (oldAvg * oldCount + rating) / (oldCount + 1)}.
     *
     * <p>Incremental rather than a {@code SELECT AVG(rating)} over the review table, so the cost
     * of a submission does not grow with the number of reviews an advisor has already collected.
     *
     * <p>The trade is rounding: the stored average is the only memory of prior ratings, so each
     * step reconstructs the total from an already-rounded figure. The error does not run away —
     * the reconstruction multiplies the stored value by {@code oldCount} and the division divides
     * it straight back by roughly the same number, so a step inherits its predecessor's error
     * rather than compounding it, leaving the displayed figure within about a hundredth of the
     * true mean. Two decimal places is also all the UI shows.
     */
    private void applyRatingToProfile(UUID advisorId, int rating) {
        AdvisorProfile profile = profileRepository.findById(advisorId)
                .orElseThrow(() -> new IllegalArgumentException("Advisor not found: " + advisorId));

        // A profile promoted from an application may never have been given a rating, and the
        // Lombok builder does not honour field initialisers — so null is a real possibility here,
        // not defensive noise.
        BigDecimal oldAverage = profile.getAverageRating() == null
                ? BigDecimal.ZERO
                : profile.getAverageRating();
        int oldCount = Math.max(profile.getReviewCount(), 0);
        int newCount = oldCount + 1;

        BigDecimal total = oldAverage
                .multiply(BigDecimal.valueOf(oldCount))
                .add(BigDecimal.valueOf(rating));
        BigDecimal newAverage = total.divide(
                BigDecimal.valueOf(newCount), RATING_SCALE, RoundingMode.HALF_UP);

        profile.setAverageRating(newAverage);
        profile.setReviewCount(newCount);
        profileRepository.save(profile);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    /**
     * Bean validation on the request already covers the HTTP path; this closes the gap for any
     * other caller of the use case and keeps an out-of-range rating from ever reaching the mean.
     */
    private static int validatedRating(SubmitReviewRequest request) {
        Integer rating = request == null ? null : request.getRating();
        if (rating == null || rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }
        return rating;
    }

    /** A whitespace-only comment is not a comment. */
    private static String normaliseComment(String comment) {
        if (comment == null) {
            return null;
        }
        String trimmed = comment.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static ReviewDto toDto(Review review) {
        return ReviewDto.builder()
                .id(review.getId())
                .advisorId(review.getAdvisorId())
                .userId(review.getUserId())
                .rating(review.getRating())
                .comment(review.getComment())
                .createdAt(review.getCreatedAt())
                .build();
    }
}
