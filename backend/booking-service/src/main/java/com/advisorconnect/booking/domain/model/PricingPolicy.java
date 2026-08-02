package com.advisorconnect.booking.domain.model;

import java.math.BigDecimal;

/**
 * The single source of truth for what a session costs.
 *
 * <p>Pricing used to live as an inline ternary in {@code BookingService}
 * ({@code durationMinutes == 30 ? "50.00" : "90.00"}), which silently charged $90 for
 * <em>any</em> duration that was not exactly 30 — including the 31..59 minute range that
 * {@code CreateBookingRequest}'s old {@code @Min(30) @Max(60)} validation let through.
 *
 * <p>Only 30- and 60-minute sessions exist as products; every other duration is a bug
 * somewhere upstream and is rejected loudly rather than priced by accident. Request
 * validation rejects unsupported durations first — this class is the defence-in-depth
 * layer behind it, and covers callers that bypass the web layer.
 */
public final class PricingPolicy {

    private PricingPolicy() {
        // policy holder — not instantiable
    }

    /**
     * @param durationMinutes session length; must be exactly 30 or 60
     * @return the amount to charge, as an exact 2-decimal {@link BigDecimal}
     * @throws IllegalArgumentException if the duration is not a supported product
     */
    public static BigDecimal priceFor(int durationMinutes) {
        return switch (durationMinutes) {
            case 30 -> new BigDecimal("50.00");
            case 60 -> new BigDecimal("90.00");
            default -> throw new IllegalArgumentException(
                    "Unsupported session duration: " + durationMinutes
                            + " minutes. Only 30 or 60 minute sessions are supported.");
        };
    }
}
