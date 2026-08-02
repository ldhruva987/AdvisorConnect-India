package com.advisorconnect.advisor.application;

import com.advisorconnect.advisor.adapter.in.web.dto.ReviewDto;
import com.advisorconnect.advisor.adapter.in.web.dto.SubmitReviewRequest;
import com.advisorconnect.advisor.domain.model.AdvisorProfile;
import com.advisorconnect.advisor.domain.model.BookingCompletionRecord;
import com.advisorconnect.advisor.domain.model.Review;
import com.advisorconnect.advisor.domain.model.ReviewNotAllowedException;
import com.advisorconnect.advisor.domain.port.out.AdvisorProfileRepository;
import com.advisorconnect.advisor.domain.port.out.BookingCompletionRepository;
import com.advisorconnect.advisor.domain.port.out.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Reviews are the one place in this service where a write to one table has to move a denormalised
 * figure on another, so the tests here are weighted towards two things: that an ineligible caller
 * never gets past the gate, and that the running mean lands on the same number a full
 * {@code AVG(rating)} would have produced.
 */
@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock private ReviewRepository reviewRepository;
    @Mock private BookingCompletionRepository bookingCompletionRepository;
    @Mock private AdvisorProfileRepository profileRepository;

    @InjectMocks
    private ReviewService service;

    private UUID advisorId;
    private UUID reviewerId;
    private UUID bookingId;

    @BeforeEach
    void setUp() {
        advisorId = UUID.randomUUID();
        reviewerId = UUID.randomUUID();
        bookingId = UUID.randomUUID();
    }

    // ═══════════════════════════════════════════════════════════════ eligibility

    @Nested
    @DisplayName("eligibility")
    class Eligibility {

        /**
         * The common case behind this branch is a client reviewing an advisor they never booked.
         * Nothing must be written — not the review, and above all not the advisor's average, which
         * has no recompute job behind it to undo a bad fold.
         */
        @Test
        @DisplayName("no completed session with this advisor is refused, and writes nothing")
        void noCompletedBookingIsRefused() {
            given(bookingCompletionRepository.findOldestUnreviewed(reviewerId, advisorId))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> service.submitReview(advisorId, reviewerId, request(5, "great")))
                    .isInstanceOf(ReviewNotAllowedException.class)
                    .hasMessageContaining("No completed, unreviewed session");

            verify(reviewRepository, never()).save(any());
            verify(bookingCompletionRepository, never()).save(any());
            verifyNoInteractions(profileRepository);
        }

        /**
         * The second review of a single session is refused by the same lookup as the first case:
         * accepting a review spends the booking, and a spent booking is no longer unreviewed. So
         * this is not really a separate rule, it is the eligibility rule observed a second time —
         * which is worth pinning, because it means the double-review defence survives even if the
         * {@code reviews.booking_id} constraint were dropped.
         */
        @Test
        @DisplayName("a second review is refused because the booking is no longer unreviewed")
        void secondReviewIsRefusedOnceTheBookingIsSpent() {
            BookingCompletionRecord record = completedBooking();
            given(bookingCompletionRepository.findOldestUnreviewed(reviewerId, advisorId))
                    .willReturn(Optional.of(record))   // first call: eligible
                    .willReturn(Optional.empty());     // second call: spent by the first review
            given(reviewRepository.existsByBookingId(bookingId)).willReturn(false);
            given(reviewRepository.save(any())).willAnswer(i -> i.getArgument(0));
            given(profileRepository.findById(advisorId)).willReturn(Optional.of(profile(null, 0)));

            service.submitReview(advisorId, reviewerId, request(5, "first"));

            assertThatThrownBy(() -> service.submitReview(advisorId, reviewerId, request(1, "again")))
                    .isInstanceOf(ReviewNotAllowedException.class);

            // Exactly one review, and exactly one fold into the average.
            verify(reviewRepository).save(any());
            verify(profileRepository).save(any());
        }

        /**
         * The flag on the completion record and the review table can drift — a redelivered
         * completion event that reset the row, a half-applied migration, a manual fix in prod. The
         * service checks both, so the answer stays 409 rather than an opaque constraint violation.
         */
        @Test
        @DisplayName("a booking that already has a review row is refused even if the flag says otherwise")
        void existingReviewRowIsRefusedDespiteAnUnreviewedFlag() {
            given(bookingCompletionRepository.findOldestUnreviewed(reviewerId, advisorId))
                    .willReturn(Optional.of(completedBooking()));
            given(reviewRepository.existsByBookingId(bookingId)).willReturn(true);

            assertThatThrownBy(() -> service.submitReview(advisorId, reviewerId, request(4, null)))
                    .isInstanceOf(ReviewNotAllowedException.class)
                    .hasMessageContaining("already been reviewed");

            verify(reviewRepository, never()).save(any());
            verify(bookingCompletionRepository, never()).save(any());
            verifyNoInteractions(profileRepository);
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -1, 6, 100})
        @DisplayName("an out-of-range rating is refused before any lookup happens")
        void outOfRangeRatingIsRefused(int rating) {
            assertThatThrownBy(() -> service.submitReview(advisorId, reviewerId, request(rating, null)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("between 1 and 5");

            verifyNoInteractions(bookingCompletionRepository, reviewRepository, profileRepository);
        }

        @Test
        @DisplayName("a missing rating is refused rather than folded in as a zero")
        void missingRatingIsRefused() {
            assertThatThrownBy(() -> service.submitReview(advisorId, reviewerId, request(null, "nice")))
                    .isInstanceOf(IllegalArgumentException.class);

            verifyNoInteractions(bookingCompletionRepository, reviewRepository, profileRepository);
        }
    }

    // ═════════════════════════════════════════════════════════ the accepted path

    @Nested
    @DisplayName("accepting a review")
    class Accepting {

        @Test
        @DisplayName("the review is saved against the booking it was earned by")
        void savesReviewAgainstTheEligibleBooking() {
            givenEligibleBooking();
            given(profileRepository.findById(advisorId)).willReturn(Optional.of(profile(null, 0)));

            ReviewDto dto = service.submitReview(advisorId, reviewerId, request(4, "  solid advice  "));

            Review saved = captureSavedReview();
            assertThat(saved.getAdvisorId()).isEqualTo(advisorId);
            assertThat(saved.getUserId()).isEqualTo(reviewerId);
            assertThat(saved.getBookingId()).isEqualTo(bookingId);
            assertThat(saved.getRating()).isEqualTo(4);
            assertThat(saved.getComment()).isEqualTo("solid advice");
            assertThat(saved.getCreatedAt()).isNotNull();

            assertThat(dto.getAdvisorId()).isEqualTo(advisorId);
            assertThat(dto.getUserId()).isEqualTo(reviewerId);
            assertThat(dto.getRating()).isEqualTo(4);
            assertThat(dto.getComment()).isEqualTo("solid advice");
        }

        /**
         * The returned DTO is what the profile page renders, and {@code bookingId} is an internal
         * eligibility token — publishing it would tell any reader which session produced which
         * review.
         */
        @Test
        @DisplayName("the returned DTO carries no booking id")
        void dtoOmitsBookingId() {
            givenEligibleBooking();
            given(profileRepository.findById(advisorId)).willReturn(Optional.of(profile(null, 0)));

            ReviewDto dto = service.submitReview(advisorId, reviewerId, request(5, null));

            assertThat(dto).hasNoNullFieldsOrPropertiesExcept("id", "comment");
            assertThat(ReviewDto.class.getDeclaredFields())
                    .noneMatch(f -> f.getName().equals("bookingId"));
        }

        @Test
        @DisplayName("the booking is marked reviewed so it cannot be spent twice")
        void marksBookingReviewed() {
            BookingCompletionRecord record = completedBooking();
            given(bookingCompletionRepository.findOldestUnreviewed(reviewerId, advisorId))
                    .willReturn(Optional.of(record));
            given(reviewRepository.existsByBookingId(bookingId)).willReturn(false);
            given(reviewRepository.save(any())).willAnswer(i -> i.getArgument(0));
            given(profileRepository.findById(advisorId)).willReturn(Optional.of(profile(null, 0)));

            service.submitReview(advisorId, reviewerId, request(3, null));

            ArgumentCaptor<BookingCompletionRecord> captor =
                    ArgumentCaptor.forClass(BookingCompletionRecord.class);
            verify(bookingCompletionRepository).save(captor.capture());
            assertThat(captor.getValue().isReviewed()).isTrue();
            assertThat(captor.getValue().getBookingId()).isEqualTo(bookingId);
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "\n\t "})
        @DisplayName("a whitespace-only comment is stored as null, not as blank text")
        void blankCommentBecomesNull(String comment) {
            givenEligibleBooking();
            given(profileRepository.findById(advisorId)).willReturn(Optional.of(profile(null, 0)));

            service.submitReview(advisorId, reviewerId, request(5, comment));

            assertThat(captureSavedReview().getComment()).isNull();
        }

        @Test
        @DisplayName("a rating with no comment is a valid review")
        void bareRatingIsAccepted() {
            givenEligibleBooking();
            given(profileRepository.findById(advisorId)).willReturn(Optional.of(profile(null, 0)));

            ReviewDto dto = service.submitReview(advisorId, reviewerId, request(5, null));

            assertThat(dto.getComment()).isNull();
            assertThat(dto.getRating()).isEqualTo(5);
        }

        /**
         * A review whose advisor has no profile row cannot be folded into any average, so it must
         * not be half-accepted. The {@code @Transactional} boundary is what actually rolls the
         * review row back; the test's job is to prove the service throws rather than swallowing it.
         */
        @Test
        @DisplayName("a missing advisor profile fails the whole submission")
        void missingProfileFailsTheSubmission() {
            givenEligibleBooking();
            given(profileRepository.findById(advisorId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> service.submitReview(advisorId, reviewerId, request(5, null)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Advisor not found");

            verify(profileRepository, never()).save(any());
        }
    }

    // ══════════════════════════════════════════════════════════ incremental mean

    @Nested
    @DisplayName("incremental mean")
    class IncrementalMean {

        /**
         * The concrete worked example: an advisor sitting on 4.00 from 3 reviews receives a 5.
         * {@code (4.00 * 3 + 5) / 4 = 17 / 4 = 4.25}, and the count moves to 4.
         */
        @Test
        @DisplayName("4.00 over 3 reviews plus a 5 gives 4.25 over 4")
        void foldsOneRatingIntoTheRunningMean() {
            givenEligibleBooking();
            given(profileRepository.findById(advisorId))
                    .willReturn(Optional.of(profile(new BigDecimal("4.00"), 3)));

            service.submitReview(advisorId, reviewerId, request(5, null));

            AdvisorProfile saved = captureSavedProfile();
            assertThat(saved.getAverageRating()).isEqualByComparingTo("4.25");
            assertThat(saved.getReviewCount()).isEqualTo(4);
        }

        /**
         * A profile promoted from an approved application is built by the Lombok builder, which
         * does not run field initialisers — so {@code averageRating} really can be null here, and
         * the very first review is the case most likely to hit it.
         */
        @Test
        @DisplayName("the first review on a never-rated profile sets the average to itself")
        void firstReviewOnNullAverage() {
            givenEligibleBooking();
            given(profileRepository.findById(advisorId))
                    .willReturn(Optional.of(profile(null, 0)));

            service.submitReview(advisorId, reviewerId, request(5, null));

            AdvisorProfile saved = captureSavedProfile();
            assertThat(saved.getAverageRating()).isEqualByComparingTo("5.00");
            assertThat(saved.getReviewCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("the first review on a zeroed profile likewise sets the average to itself")
        void firstReviewOnZeroedProfile() {
            givenEligibleBooking();
            given(profileRepository.findById(advisorId))
                    .willReturn(Optional.of(profile(BigDecimal.ZERO, 0)));

            service.submitReview(advisorId, reviewerId, request(3, null));

            assertThat(captureSavedProfile().getAverageRating()).isEqualByComparingTo("3.00");
        }

        @ParameterizedTest(name = "{0} over {1} reviews plus a {2} gives {3}")
        @CsvSource({
                // oldAvg, oldCount, rating, expected
                "4.00,  3,  5,  4.25",   // 17 / 4
                "4.00,  1,  2,  3.00",   // 6  / 2
                "5.00,  4,  1,  4.20",   // 21 / 5
                "3.00,  2,  4,  3.33",   // 10 / 3, HALF_UP to two places
                "4.33,  3,  2,  3.75",   // 14.99 / 4 = 3.7475 -> 3.75, the true mean of 4,5,4,2
                "1.00, 99,  1,  1.00",   // a long run of identical ratings does not drift
        })
        @DisplayName("worked examples of the fold, including rounding")
        void workedExamples(String oldAverage, int oldCount, int rating, String expected) {
            givenEligibleBooking();
            given(profileRepository.findById(advisorId))
                    .willReturn(Optional.of(profile(new BigDecimal(oldAverage), oldCount)));

            service.submitReview(advisorId, reviewerId, request(rating, null));

            AdvisorProfile saved = captureSavedProfile();
            assertThat(saved.getAverageRating()).isEqualByComparingTo(expected);
            assertThat(saved.getReviewCount()).isEqualTo(oldCount + 1);
        }

        /**
         * The stored average is the only memory of prior ratings, so each step reconstructs the
         * total from an already-rounded figure. That is the accepted cost of not running
         * {@code AVG(rating)} on every submission — but the error must not accumulate. Ten reviews
         * of 4 and 5 alternating have a true mean of 4.5; the incremental result is checked
         * against it rather than against a hardcoded figure.
         */
        @Test
        @DisplayName("rounding error does not accumulate over a run of submissions")
        void roundingDoesNotDrift() {
            int[] ratings = {4, 5, 4, 5, 4, 5, 4, 5, 4, 5};
            AdvisorProfile profile = profile(null, 0);

            given(bookingCompletionRepository.findOldestUnreviewed(reviewerId, advisorId))
                    .willAnswer(i -> Optional.of(completedBooking()));
            given(reviewRepository.existsByBookingId(any())).willReturn(false);
            given(reviewRepository.save(any())).willAnswer(i -> i.getArgument(0));
            given(profileRepository.findById(advisorId)).willReturn(Optional.of(profile));

            for (int rating : ratings) {
                service.submitReview(advisorId, reviewerId, request(rating, null));
            }

            assertThat(profile.getReviewCount()).isEqualTo(ratings.length);
            assertThat(profile.getAverageRating())
                    .isCloseTo(new BigDecimal("4.50"), org.assertj.core.data.Offset.offset(new BigDecimal("0.01")));
        }

        /**
         * A count that has somehow gone negative would make the reconstructed total negative too
         * and could push the stored average outside 1–5 permanently. Clamping is cheap insurance.
         */
        @Test
        @DisplayName("a negative review count is clamped rather than corrupting the average")
        void negativeCountIsClamped() {
            givenEligibleBooking();
            given(profileRepository.findById(advisorId))
                    .willReturn(Optional.of(profile(new BigDecimal("4.00"), -5)));

            service.submitReview(advisorId, reviewerId, request(3, null));

            AdvisorProfile saved = captureSavedProfile();
            assertThat(saved.getAverageRating()).isEqualByComparingTo("3.00");
            assertThat(saved.getReviewCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("the stored average always carries two decimal places")
        void averageIsScaledToTwoDecimals() {
            givenEligibleBooking();
            given(profileRepository.findById(advisorId))
                    .willReturn(Optional.of(profile(new BigDecimal("4.00"), 1)));

            service.submitReview(advisorId, reviewerId, request(4, null));

            assertThat(captureSavedProfile().getAverageRating().scale()).isEqualTo(2);
        }
    }

    // ══════════════════════════════════════════════════════════════════ listing

    @Nested
    @DisplayName("listing")
    class Listing {

        @Test
        @DisplayName("the public listing maps entities to DTOs and preserves paging")
        void listingMapsToDtos() {
            Pageable pageable = PageRequest.of(0, 20);
            Review review = Review.builder()
                    .id(UUID.randomUUID())
                    .advisorId(advisorId)
                    .userId(reviewerId)
                    .bookingId(bookingId)
                    .rating(5)
                    .comment("clear and to the point")
                    .createdAt(Instant.now())
                    .build();
            given(reviewRepository.findByAdvisorId(advisorId, pageable))
                    .willReturn(new PageImpl<>(List.of(review), pageable, 1));

            Page<ReviewDto> page = service.getReviews(advisorId, pageable);

            assertThat(page.getTotalElements()).isEqualTo(1);
            ReviewDto dto = page.getContent().get(0);
            assertThat(dto.getId()).isEqualTo(review.getId());
            assertThat(dto.getRating()).isEqualTo(5);
            assertThat(dto.getComment()).isEqualTo("clear and to the point");
            assertThat(dto.getUserId()).isEqualTo(reviewerId);
        }

        @Test
        @DisplayName("an advisor with no reviews yields an empty page, not an error")
        void emptyListing() {
            Pageable pageable = PageRequest.of(0, 20);
            given(reviewRepository.findByAdvisorId(advisorId, pageable)).willReturn(Page.empty(pageable));

            assertThat(service.getReviews(advisorId, pageable)).isEmpty();
        }
    }

    // ────────────────────────────────────────────────────────────────── helpers

    private void givenEligibleBooking() {
        given(bookingCompletionRepository.findOldestUnreviewed(reviewerId, advisorId))
                .willReturn(Optional.of(completedBooking()));
        given(reviewRepository.existsByBookingId(bookingId)).willReturn(false);
        given(reviewRepository.save(any())).willAnswer(i -> i.getArgument(0));
    }

    private BookingCompletionRecord completedBooking() {
        return BookingCompletionRecord.builder()
                .bookingId(bookingId)
                .userId(reviewerId)
                .advisorId(advisorId)
                .completedAt(Instant.now().minusSeconds(3600))
                .reviewed(false)
                .build();
    }

    private AdvisorProfile profile(BigDecimal averageRating, int reviewCount) {
        return AdvisorProfile.builder()
                .id(advisorId)
                .username("advisor-" + advisorId.toString().substring(0, 8))
                .professionalTitle("Chartered Accountant")
                .averageRating(averageRating)
                .reviewCount(reviewCount)
                .build();
    }

    private static SubmitReviewRequest request(Integer rating, String comment) {
        return SubmitReviewRequest.builder().rating(rating).comment(comment).build();
    }

    private Review captureSavedReview() {
        ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
        verify(reviewRepository).save(captor.capture());
        return captor.getValue();
    }

    private AdvisorProfile captureSavedProfile() {
        ArgumentCaptor<AdvisorProfile> captor = ArgumentCaptor.forClass(AdvisorProfile.class);
        verify(profileRepository).save(captor.capture());
        return captor.getValue();
    }
}
