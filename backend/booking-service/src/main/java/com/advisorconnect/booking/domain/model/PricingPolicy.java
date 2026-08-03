package com.advisorconnect.booking.domain.model;

import java.math.BigDecimal;

/**
 * The single source of truth for what a session costs.
 *
 * <p>Priced in Indian rupees for this market rather than as an FX conversion of the US build's
 * $50/$90: at current rates that would land around ₹4,000/₹7,500, far above what India's
 * online-counseling and coaching market actually charges per session (closer to ₹500–1,500).
 * ₹500/₹900 tracks that local rate instead of preserving revenue-per-session parity with the US
 * build.
 *
 * <p>Only 30- and 60-minute sessions exist as products; every other duration is a bug somewhere
 * upstream and is rejected loudly rather than priced by accident. Request validation rejects
 * unsupported durations first — this class is the defence-in-depth layer behind it, and covers
 * callers that bypass the web layer.
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
            case 30 -> new BigDecimal("500.00");
            case 60 -> new BigDecimal("900.00");
            default -> throw new IllegalArgumentException(
                    "Unsupported session duration: " + durationMinutes
                            + " minutes. Only 30 or 60 minute sessions are supported.");
        };
    }
}
