package com.advisorconnect.advisor.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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
 * A rating a client left for an advisor after a completed session.
 *
 * <p>{@code bookingId} carries a unique constraint. The service already refuses a second review
 * by consulting {@link BookingCompletionRecord#isReviewed()}, but that check and the insert are
 * two statements; the constraint is what holds under two concurrent submissions for the same
 * booking, where both transactions can read {@code reviewed == false} before either writes.
 */
@Entity
@Table(name = "reviews")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID advisorId;

    /** The reviewer. */
    @Column(nullable = false)
    private UUID userId;

    /** The session this review was earned by — one review per booking, forever. */
    @Column(nullable = false, unique = true)
    private UUID bookingId;

    /** 1–5. Bounded at the DTO by bean validation and re-checked in the service. */
    @Column(nullable = false)
    private int rating;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(nullable = false)
    private Instant createdAt;
}
