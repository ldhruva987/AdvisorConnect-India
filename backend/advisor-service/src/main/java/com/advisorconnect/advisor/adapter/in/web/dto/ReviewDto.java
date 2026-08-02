package com.advisorconnect.advisor.adapter.in.web.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * A review as returned on the public listing.
 *
 * <p>Carries {@code userId} but deliberately no reviewer name or email — advisor-service holds
 * neither, and the public browse surface has no business leaking the email projection it keeps
 * for notification purposes. The {@code bookingId} is likewise omitted: it is an internal
 * eligibility token, not something a reader of the profile needs.
 */
@Data
@Builder
public class ReviewDto {
    private UUID id;
    private UUID advisorId;
    private UUID userId;
    private int rating;
    private String comment;
    private Instant createdAt;
}
