package com.advisorconnect.booking.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class CreateBookingRequest {

    @NotNull
    private UUID advisorId;

    @NotNull
    private Instant sessionDateTime;

    /**
     * Session length. Only the two products that actually exist are accepted.
     *
     * <p>This was {@code @Min(30) @Max(60)}, which admitted 31..59 — durations with no price,
     * and which the pricing ternary quietly charged ₹900 for.
     */
    @NotNull
    private Integer durationMinutes;

    /**
     * Bean Validation has no "one of these literals" constraint for numbers, and the codebase
     * has no custom-validator precedent to follow, so the cheapest correct expression of the
     * rule is an {@code @AssertTrue} check.
     *
     * <p>Returns {@code true} when the value is null so that a missing duration reports only
     * the {@code @NotNull} violation instead of two overlapping errors.
     */
    @JsonIgnore
    @AssertTrue(message = "durationMinutes must be either 30 or 60")
    public boolean isDurationMinutesSupported() {
        return durationMinutes == null || durationMinutes == 30 || durationMinutes == 60;
    }
}
