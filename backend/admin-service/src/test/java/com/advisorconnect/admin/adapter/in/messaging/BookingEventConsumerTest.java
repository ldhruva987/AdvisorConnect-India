package com.advisorconnect.admin.adapter.in.messaging;

import com.advisorconnect.admin.domain.port.out.PlatformCountersRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class BookingEventConsumerTest {

    @Mock
    private PlatformCountersRepository counters;

    @InjectMocks
    private BookingEventConsumer consumer;

    @Test
    @DisplayName("a completed session's charge is booked as revenue, in cents")
    void completedSessionAddsRevenue() {
        consumer.onBookingCompleted(completedWithAmount("90.00"));

        then(counters).should().addPlatformRevenueCents(9_000);
    }

    @Test
    @DisplayName("an amount with odd cents converts exactly")
    void oddCentsSurviveTheConversion() {
        // 90.10 has no exact binary representation; via double this lands on 9009 or 9010
        // depending on the rounding mode. BigDecimal makes it unambiguous.
        consumer.onBookingCompleted(completedWithAmount("90.10"));

        then(counters).should().addPlatformRevenueCents(9_010);
    }

    @Test
    @DisplayName("a whole-dollar amount with no decimal point converts")
    void wholeDollarsConvert() {
        consumer.onBookingCompleted(completedWithAmount("45"));

        then(counters).should().addPlatformRevenueCents(4_500);
    }

    @Test
    @DisplayName("a numeric (non-string) amount converts too")
    void numericAmountConverts() {
        // JsonDeserializer binds an unquoted JSON number to Double/Integer rather than String.
        Map<String, Object> event = completed();
        event.put("amountCharged", new BigDecimal("120.50"));

        consumer.onBookingCompleted(event);

        then(counters).should().addPlatformRevenueCents(12_050);
    }

    @Test
    @DisplayName("revenue accumulates across sessions")
    void revenueAccumulates() {
        consumer.onBookingCompleted(completedWithAmount("90.00"));
        consumer.onBookingCompleted(completedWithAmount("45.00"));

        then(counters).should().addPlatformRevenueCents(9_000);
        then(counters).should().addPlatformRevenueCents(4_500);
    }

    @Test
    @DisplayName("an amount-less booking.completed leaves revenue untouched rather than adding zero noise")
    void missingAmountIsSkipped() {
        // This is the payload booking-service publishes today — BookingEventPublisher's
        // booking.completed carries only bookingId/userId/advisorId. The listener must be inert
        // rather than throwing, so the topic keeps flowing until the amount is added upstream.
        consumer.onBookingCompleted(completed());

        then(counters).should(never()).addPlatformRevenueCents(anyLong());
    }

    @Test
    @DisplayName("an unparseable amount is skipped, not guessed at")
    void malformedAmountIsSkipped() {
        consumer.onBookingCompleted(completedWithAmount("ninety dollars"));

        then(counters).should(never()).addPlatformRevenueCents(anyLong());
    }

    @Test
    @DisplayName("a blank amount is treated as absent")
    void blankAmountIsSkipped() {
        consumer.onBookingCompleted(completedWithAmount("   "));

        then(counters).should(never()).addPlatformRevenueCents(anyLong());
    }

    @Test
    @DisplayName("a refunded (negative) amount is applied as a negative delta")
    void negativeAmountsAreApplied() {
        consumer.onBookingCompleted(completedWithAmount("-30.00"));

        then(counters).should().addPlatformRevenueCents(-3_000);
    }

    /**
     * Mirrors BookingEventPublisher.publishBookingCompleted, which is the only booking topic this
     * service listens to — booking.created fires for an unpaid PENDING booking.
     */
    private static Map<String, Object> completed() {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "BOOKING_COMPLETED");
        event.put("bookingId", UUID.randomUUID().toString());
        event.put("userId", UUID.randomUUID().toString());
        event.put("advisorId", UUID.randomUUID().toString());
        return event;
    }

    private static Map<String, Object> completedWithAmount(String amountCharged) {
        Map<String, Object> event = completed();
        event.put("amountCharged", amountCharged);
        return event;
    }
}
