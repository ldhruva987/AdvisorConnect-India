package com.advisorconnect.advisor.adapter.in.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitReviewRequest {

    /**
     * Boxed rather than primitive so a missing field fails validation as null instead of
     * silently arriving as a 0 that {@code @Min(1)} would then report as an out-of-range rating.
     */
    @Min(value = 1, message = "Rating must be between 1 and 5")
    @Max(value = 5, message = "Rating must be between 1 and 5")
    private Integer rating;

    /** Optional — a bare star rating is a valid review. */
    @Size(max = 2000, message = "Comment must be at most 2000 characters")
    private String comment;
}
