package com.advisorconnect.booking.adapter.in.web.dto;

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

    @NotNull
    @Min(30)
    @Max(60)
    private Integer durationMinutes;

    @NotBlank
    private String stripePaymentMethodId;
}
