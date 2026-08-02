package com.advisorconnect.advisor.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A local projection of booking-service's {@code booking.completed} stream: the record that a
 * given user actually sat through a session with a given advisor.
 *
 * <p>This is what makes review eligibility answerable without a synchronous call into
 * booking-service. Reviews live in advisor-service precisely so the {@code averageRating}
 * recompute stays inside one local transaction; that only works if the eligibility check is
 * local too, hence this read model rather than a Feign lookup.
 *
 * <p>{@code bookingId} is the primary key and is assigned by booking-service, not generated
 * here. That is the whole idempotency story: at-least-once delivery and {@code earliest}-offset
 * replay both land on a row that already exists.
 *
 * <p>{@code reviewed} is owned by this service, not by the event stream — it flips when
 * {@code ReviewService} accepts a review. A redelivered {@code booking.completed} must therefore
 * never overwrite an existing row, or replaying the topic would silently re-open every session
 * for a second review. See {@code BookingCompletedEventConsumer}, which skips rather than
 * upserts for exactly this reason.
 */
@Entity
@Table(name = "booking_completion_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingCompletionRecord {

    /** booking-service's booking id — externally assigned, which is what makes writes idempotent. */
    @Id
    private UUID bookingId;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private UUID advisorId;

    @Column(nullable = false)
    private Instant completedAt;

    /**
     * Whether this session has been spent on a review. Owned locally; never written by the
     * consumer.
     */
    @Column(nullable = false)
    private boolean reviewed;
}
