package com.advisorconnect.booking.adapter.out.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes booking lifecycle events to Kafka.
 *
 * <p>Extracted from {@code BookingService}, which previously held a {@code KafkaTemplate}
 * and built event payloads inline. Mirrors {@code AdvisorEventPublisher} in advisor-service
 * so every service names, keys and logs its events the same way.
 *
 * <p>Topic names and payload shapes are additive over what {@code BookingService} used to
 * send — notification-service consumes these, so existing keys keep their names and meanings
 * and only {@code userEmail} is new.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BookingEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * @param userEmail where notification-service sends the confirmation. booking-service does
     *                  not own the email; it comes from the local {@code user_email_cache}
     *                  projection of auth-service's {@code user.registered} stream. Empty when
     *                  the cache has not seen the user yet — a gap in the projection must not
     *                  block a booking, so the event ships without it and the notification is
     *                  what degrades. Every booking event previously omitted this field
     *                  entirely, which is why no booking email was ever delivered:
     *                  {@code EmailNotificationService} reads {@code userEmail} and returns
     *                  without sending when it is blank.
     */
    public void publishBookingCreated(UUID bookingId, UUID userId, UUID advisorId, String userEmail) {
        var payload = Map.of(
                "eventType", "BOOKING_CREATED",
                "bookingId", bookingId.toString(),
                "userId", userId.toString(),
                "advisorId", advisorId.toString(),
                // Map.of rejects nulls outright, and the consumer's contract is "blank means
                // unknown", so a missing email is normalised to the empty string here.
                "userEmail", nullSafe(userEmail)
        );
        kafkaTemplate.send("booking.created", bookingId.toString(), payload);
        log.info("Published BOOKING_CREATED for bookingId={} emailPresent={}",
                bookingId, isPresent(userEmail));
    }

    /** @param userEmail see {@link #publishBookingCreated}. */
    public void publishBookingCancelled(UUID bookingId, UUID userId, String userEmail) {
        var payload = Map.of(
                "eventType", "BOOKING_CANCELLED",
                "bookingId", bookingId.toString(),
                "userId", userId.toString(),
                "userEmail", nullSafe(userEmail)
        );
        kafkaTemplate.send("booking.cancelled", bookingId.toString(), payload);
        log.info("Published BOOKING_CANCELLED for bookingId={} emailPresent={}",
                bookingId, isPresent(userEmail));
    }

    /**
     * A session has finished. Emitted by the completion sweep, not by any user action — nothing
     * previously moved a booking past {@code CONFIRMED}, so this event had no producer.
     *
     * <p>Carries the advisor id as well as the user id because the obvious consumer is a
     * "leave a review" prompt, which needs to know who is being reviewed.
     *
     * @param userEmail see {@link #publishBookingCreated}.
     * @param amountCharged what the client actually paid for the session, and the only point at
     *                      which that number leaves booking-service. admin-service totals it into
     *                      the dashboard's platform revenue figure; the event previously carried
     *                      no amount at all, so that figure was permanently $0.00 however many
     *                      sessions completed. Sent as a decimal string ({@code "90.00"}) rather
     *                      than a JSON number on purpose: the consumer re-parses it with
     *                      {@code new BigDecimal(String)} to keep the value exact, and a number
     *                      would round-trip through {@code double} and reintroduce the drift that
     *                      parsing step exists to avoid.
     */
    public void publishBookingCompleted(UUID bookingId, UUID userId, UUID advisorId,
                                        String userEmail, BigDecimal amountCharged) {
        var payload = Map.of(
                "eventType", "BOOKING_COMPLETED",
                "bookingId", bookingId.toString(),
                "userId", userId.toString(),
                "advisorId", advisorId.toString(),
                "userEmail", nullSafe(userEmail),
                // Same "" convention as userEmail: Map.of rejects nulls, and the consumer treats a
                // blank amount as "no usable amount" and skips the increment rather than counting
                // a zero. A booking with no amount must not silently deflate platform revenue.
                "amountCharged", nullSafe(amountCharged)
        );
        kafkaTemplate.send("booking.completed", bookingId.toString(), payload);
        log.info("Published BOOKING_COMPLETED for bookingId={} emailPresent={} amountCharged={}",
                bookingId, isPresent(userEmail), nullSafe(amountCharged));
    }

    private static String nullSafe(String email) {
        return email == null ? "" : email;
    }

    /**
     * The amount as the consumer wants to read it: its own {@code toString}, which preserves the
     * scale the column was stored with, so {@code 90.00} stays {@code "90.00"}. Never formatted
     * with a locale — a comma decimal separator would make the value unparseable downstream.
     */
    private static String nullSafe(BigDecimal amount) {
        return amount == null ? "" : amount.toString();
    }

    private static boolean isPresent(String email) {
        return email != null && !email.isBlank();
    }
}
