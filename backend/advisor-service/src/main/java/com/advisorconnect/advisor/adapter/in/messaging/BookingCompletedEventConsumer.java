package com.advisorconnect.advisor.adapter.in.messaging;

import com.advisorconnect.advisor.domain.model.BookingCompletionRecord;
import com.advisorconnect.advisor.domain.port.out.BookingCompletionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Builds the local record of who has actually completed a session with whom, from
 * booking-service's {@code booking.completed} stream. That record is what
 * {@code ReviewService} checks before accepting a review.
 *
 * <p>Payload shape is booking-service's {@code BookingEventPublisher}: a flat map of
 * {@code eventType}, {@code bookingId}, {@code userId}, {@code advisorId}, {@code userEmail}.
 * The email is ignored here — {@code UserRegisteredEventConsumer} already owns that projection,
 * and reviews have no use for it. Read defensively: this is a cross-service contract, and
 * producer-side drift should degrade to a logged error rather than a poison-pill loop.
 *
 * <p><strong>Idempotency.</strong> Kafka is at-least-once and this consumer reads from
 * {@code earliest}, so redelivery and full-topic replay both have to be no-ops. They are — but
 * note that this is a <em>skip</em>, not the upsert used elsewhere in this service. The row
 * carries a locally-owned {@code reviewed} flag that the event knows nothing about; blindly
 * overwriting would clear it, and replaying the topic would hand every client a fresh review
 * slot for sessions they have already rated.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BookingCompletedEventConsumer {

    public static final String TOPIC_BOOKING_COMPLETED = "booking.completed";

    private final BookingCompletionRepository bookingCompletionRepository;

    @KafkaListener(topics = TOPIC_BOOKING_COMPLETED, groupId = "advisor-service")
    @Transactional
    public void onBookingCompleted(Map<String, Object> event) {
        Optional<UUID> bookingId = readUuid(event, "bookingId");
        Optional<UUID> userId = readUuid(event, "userId");
        Optional<UUID> advisorId = readUuid(event, "advisorId");

        if (bookingId.isEmpty() || userId.isEmpty() || advisorId.isEmpty()) {
            // Redelivering a structurally broken event only replays the same failure, so it is
            // logged and acknowledged rather than retried forever.
            log.error("Ignoring malformed booking.completed event: {}", event);
            return;
        }

        if (bookingCompletionRepository.existsById(bookingId.get())) {
            log.debug("booking.completed for bookingId={} already recorded; skipping",
                    bookingId.get());
            return;
        }

        bookingCompletionRepository.save(BookingCompletionRecord.builder()
                .bookingId(bookingId.get())
                .userId(userId.get())
                .advisorId(advisorId.get())
                .completedAt(Instant.now())
                .reviewed(false)
                .build());
        log.info("Recorded completed booking {} (userId={}, advisorId={}) as review-eligible",
                bookingId.get(), userId.get(), advisorId.get());
    }

    private static Optional<UUID> readUuid(Map<String, Object> event, String key) {
        Object raw = event == null ? null : event.get(key);
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(raw.toString().trim()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
