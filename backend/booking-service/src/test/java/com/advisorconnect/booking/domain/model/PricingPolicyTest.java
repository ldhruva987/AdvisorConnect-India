package com.advisorconnect.booking.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pricing was an inline ternary that charged ₹900 for every duration that was not exactly 30.
 * These tests pin the two real products and require everything else to fail loudly.
 */
class PricingPolicyTest {

    @Test
    @DisplayName("a 30-minute session costs ₹500.00")
    void thirtyMinutesCostsFiveHundred() {
        assertThat(PricingPolicy.priceFor(30)).isEqualByComparingTo(new BigDecimal("500.00"));
    }

    @Test
    @DisplayName("a 60-minute session costs ₹900.00")
    void sixtyMinutesCostsNineHundred() {
        assertThat(PricingPolicy.priceFor(60)).isEqualByComparingTo(new BigDecimal("900.00"));
    }

    @Test
    @DisplayName("prices carry exactly two decimal places, so no rounding is implied downstream")
    void priceScaleIsExact() {
        assertThat(PricingPolicy.priceFor(30).scale()).isEqualTo(2);
        assertThat(PricingPolicy.priceFor(60).scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("45 minutes is rejected rather than silently charged the 60-minute price")
    void fortyFiveMinutesThrows() {
        assertThatThrownBy(() -> PricingPolicy.priceFor(45))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("45")
                .hasMessageContaining("Only 30 or 60 minute sessions are supported");
    }

    @Test
    @DisplayName("a negative duration is rejected")
    void negativeDurationThrows() {
        assertThatThrownBy(() -> PricingPolicy.priceFor(-30))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("-30");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 29, 31, 59, 61, 90, 120})
    @DisplayName("every other duration is rejected, including the old @Min(30)/@Max(60) gap")
    void unsupportedDurationsThrow(int minutes) {
        assertThatThrownBy(() -> PricingPolicy.priceFor(minutes))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
