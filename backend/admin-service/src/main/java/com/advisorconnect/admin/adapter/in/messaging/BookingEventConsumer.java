package com.advisorconnect.admin.adapter.in.messaging;

import com.advisorconnect.admin.domain.port.out.PlatformCountersRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Optional;

/**
 * Accumulates platform revenue from finished sessions.
 *
 * <p>Only {@code booking.completed} is consumed, deliberately. {@code booking.created} fires for a
 * {@code PENDING} booking whose card has not been charged — counting it would book revenue for
 * sessions that get cancelled or whose payment fails, and the dashboard would report money the
 * platform never received.
 *
 * <p>The amount is read from {@code amountCharged} — the name of the field on booking-service's
 * {@code Booking} entity — with a fallback to {@code amount}. This listener was written before
 * {@code BookingEventPublisher.publishBookingCompleted} sent any amount at all, when the revenue
 * figure was therefore permanently $0.00; it now does, and revenue accrues without a change here.
 * The fallback and the missing-amount branch below both stay: an event whose amount is absent or
 * unparseable must leave the counter alone rather than book a zero, because a zero is
 * indistinguishable from a genuinely free session once it is summed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BookingEventConsumer {

    private static final BigDecimal CENTS_PER_UNIT = BigDecimal.valueOf(100);

    private final PlatformCountersRepository counters;

    @KafkaListener(topics = "booking.completed", groupId = "admin-service")
    @Transactional
    public void onBookingCompleted(Map<String, Object> event) {
        String bookingId = EventPayloads.stringOr(event, "bookingId", "unknown");
        Optional<Long> cents = amountInCents(event);

        if (cents.isEmpty()) {
            log.warn("booking.completed for bookingId={} carried no usable amount; "
                    + "revenue not incremented", bookingId);
            return;
        }

        counters.addPlatformRevenueCents(cents.get());
        log.info("booking.completed consumed, platformRevenueCents += {} (bookingId={})",
                cents.get(), bookingId);
    }

    /**
     * Converts the charged amount to whole cents.
     *
     * <p>Parsed via {@link BigDecimal}'s {@code String} constructor rather than through a
     * {@code double}: {@code 90.10} has no exact binary representation, and accumulating that
     * error across every session on the platform is exactly the sort of drift that makes a
     * revenue figure indefensible.
     */
    private Optional<Long> amountInCents(Map<String, Object> event) {
        String raw = EventPayloads.string(event, "amountCharged");
        if (raw == null) {
            raw = EventPayloads.string(event, "amount");
        }
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new BigDecimal(raw)
                    .multiply(CENTS_PER_UNIT)
                    // Amounts arrive with two decimal places; the rounding step only guards
                    // against a sub-cent value, and never silently truncates one away.
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValueExact());
        } catch (ArithmeticException | NumberFormatException malformed) {
            log.warn("booking.completed carried an unparseable amount '{}'; revenue not incremented",
                    raw, malformed);
            return Optional.empty();
        }
    }
}
