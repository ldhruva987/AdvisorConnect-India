package com.advisorconnect.advisor.adapter.in.messaging;

import com.advisorconnect.advisor.domain.model.BookingCompletionRecord;
import com.advisorconnect.advisor.domain.port.out.BookingCompletionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * The consumer that decides who is allowed to leave a review. Payload shape is booking-service's
 * {@code BookingEventPublisher}: {@code {eventType, bookingId, userId, advisorId, userEmail}}.
 *
 * <p>Two properties carry the weight here. The first is that a well-formed event produces exactly
 * the eligibility row {@code ReviewService} will later look for. The second is that redelivery is
 * a <em>skip</em> and not an overwrite — the row owns a locally-written {@code reviewed} flag the
 * event knows nothing about, so an upsert on replay would hand back a review slot for every
 * session that had already been rated. That distinction is invisible in production until someone
 * resets a consumer group, which is exactly why it is pinned here.
 */
@ExtendWith(MockitoExtension.class)
class BookingCompletedEventConsumerTest {

    @Mock
    private BookingCompletionRepository repository;

    @InjectMocks
    private BookingCompletedEventConsumer consumer;

    private UUID bookingId;
    private UUID userId;
    private UUID advisorId;

    @BeforeEach
    void setUp() {
        bookingId = UUID.randomUUID();
        userId = UUID.randomUUID();
        advisorId = UUID.randomUUID();
    }

    // ═════════════════════════════════════════════════════════ the recorded write

    @Nested
    @DisplayName("recording a completed booking")
    class Recording {

        @Test
        @DisplayName("a well-formed event records the session as review-eligible")
        void recordsCompletedBooking() {
            given(repository.existsById(bookingId)).willReturn(false);

            consumer.onBookingCompleted(event(bookingId, userId, advisorId));

            BookingCompletionRecord saved = captureSaved();
            assertThat(saved.getBookingId()).isEqualTo(bookingId);
            assertThat(saved.getUserId()).isEqualTo(userId);
            assertThat(saved.getAdvisorId()).isEqualTo(advisorId);
            assertThat(saved.getCompletedAt()).isNotNull().isBefore(Instant.now().plusSeconds(1));
        }

        /**
         * A freshly recorded session has not been spent yet — if this ever landed as {@code true}
         * the client would silently lose the review they are entitled to, with no error anywhere.
         */
        @Test
        @DisplayName("the new row starts unreviewed")
        void newRowIsUnreviewed() {
            given(repository.existsById(bookingId)).willReturn(false);

            consumer.onBookingCompleted(event(bookingId, userId, advisorId));

            assertThat(captureSaved().isReviewed()).isFalse();
        }

        /**
         * The id is booking-service's, not one generated here. That is the entire idempotency
         * story: the primary key is stable across deliveries, so a repeat is recognisable.
         */
        @Test
        @DisplayName("the primary key is the booking id from the event, not a fresh one")
        void primaryKeyComesFromTheEvent() {
            given(repository.existsById(bookingId)).willReturn(false);

            consumer.onBookingCompleted(event(bookingId, userId, advisorId));

            assertThat(captureSaved().getBookingId()).isEqualTo(bookingId);
        }

        /** Different sessions are separate eligibility rows, one review each. */
        @Test
        @DisplayName("two different bookings for the same pair are both recorded")
        void distinctBookingsAreBothRecorded() {
            UUID secondBooking = UUID.randomUUID();
            given(repository.existsById(any())).willReturn(false);

            consumer.onBookingCompleted(event(bookingId, userId, advisorId));
            consumer.onBookingCompleted(event(secondBooking, userId, advisorId));

            ArgumentCaptor<BookingCompletionRecord> captor =
                    ArgumentCaptor.forClass(BookingCompletionRecord.class);
            verify(repository, org.mockito.Mockito.times(2)).save(captor.capture());
            assertThat(captor.getAllValues())
                    .extracting(BookingCompletionRecord::getBookingId)
                    .containsExactly(bookingId, secondBooking);
        }

        /** The email is another consumer's business; reviews have no use for it. */
        @Test
        @DisplayName("the userEmail field on the event is ignored, not mistaken for anything")
        void userEmailIsIgnored() {
            given(repository.existsById(bookingId)).willReturn(false);
            Map<String, Object> event = event(bookingId, userId, advisorId);
            event.put("userEmail", "ada@example.com");

            consumer.onBookingCompleted(event);

            BookingCompletionRecord saved = captureSaved();
            assertThat(saved.getUserId()).isEqualTo(userId);
            assertThat(saved.getAdvisorId()).isEqualTo(advisorId);
        }

        @Test
        @DisplayName("surrounding whitespace on the ids is tolerated")
        void trimsIds() {
            given(repository.existsById(bookingId)).willReturn(false);
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "BOOKING_COMPLETED");
            event.put("bookingId", "  " + bookingId + "  ");
            event.put("userId", " " + userId + " ");
            event.put("advisorId", "\t" + advisorId + "\n");

            consumer.onBookingCompleted(event);

            BookingCompletionRecord saved = captureSaved();
            assertThat(saved.getBookingId()).isEqualTo(bookingId);
            assertThat(saved.getUserId()).isEqualTo(userId);
            assertThat(saved.getAdvisorId()).isEqualTo(advisorId);
        }
    }

    // ═══════════════════════════════════════════════════════════════ idempotency

    @Nested
    @DisplayName("idempotency")
    class Idempotency {

        /**
         * Kafka is at-least-once, so the same event arriving twice is routine rather than
         * exceptional. The second delivery must not write at all.
         */
        @Test
        @DisplayName("a redelivered event is skipped rather than written again")
        void redeliveryIsSkipped() {
            Map<String, Object> event = event(bookingId, userId, advisorId);
            given(repository.existsById(bookingId)).willReturn(false, true, true);

            consumer.onBookingCompleted(event);
            consumer.onBookingCompleted(event);
            consumer.onBookingCompleted(event);

            // Exactly one write across three deliveries: the end state after a replay of the whole
            // topic is identical to the end state after a single delivery.
            verify(repository, org.mockito.Mockito.times(1)).save(any());
        }

        /**
         * The point of skipping rather than upserting. Once a client has spent the session on a
         * review, a replayed {@code booking.completed} must not reset {@code reviewed} — the row
         * must not be touched at all. An upsert here would let the whole platform be re-reviewed
         * by resetting one consumer group's offsets.
         */
        @Test
        @DisplayName("redelivery of an already-reviewed booking does not reset the reviewed flag")
        void redeliveryDoesNotClearTheReviewedFlag() {
            given(repository.existsById(bookingId)).willReturn(true);

            consumer.onBookingCompleted(event(bookingId, userId, advisorId));

            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("redelivery does not throw, so the offset still advances")
        void redeliveryDoesNotThrow() {
            given(repository.existsById(bookingId)).willReturn(true);

            assertThatCode(() -> consumer.onBookingCompleted(event(bookingId, userId, advisorId)))
                    .doesNotThrowAnyException();
        }

        /**
         * Booking-service is the sole author of the pairing. If a later delivery of the same
         * booking id carried a different advisor — producer bug, or a genuinely re-keyed event —
         * the existing row wins, because it may already have been spent against the original pair.
         */
        @Test
        @DisplayName("a redelivery carrying different ids still does not overwrite the row")
        void redeliveryWithDivergentPayloadDoesNotOverwrite() {
            given(repository.existsById(bookingId)).willReturn(true);

            consumer.onBookingCompleted(event(bookingId, UUID.randomUUID(), UUID.randomUUID()));

            verify(repository, never()).save(any());
        }
    }

    // ═════════════════════════════════════════════════════════════ malformed input

    @Nested
    @DisplayName("malformed events")
    class Malformed {

        /**
         * There is no dead-letter topic behind this listener. Throwing on a structurally broken
         * event would wedge the partition and stop every later completion from being recorded —
         * which shows up as clients being told they never had a session. Log and move on.
         */
        @ParameterizedTest
        @ValueSource(strings = {"not-a-uuid", "", "   "})
        @DisplayName("a malformed bookingId is dropped rather than poisoning the partition")
        void malformedBookingIdIsDropped(String raw) {
            Map<String, Object> event = event(bookingId, userId, advisorId);
            event.put("bookingId", raw);

            assertThatCode(() -> consumer.onBookingCompleted(event)).doesNotThrowAnyException();

            verifyNoInteractions(repository);
        }

        @ParameterizedTest
        @ValueSource(strings = {"userId", "advisorId", "bookingId"})
        @DisplayName("a missing required id is dropped without touching the repository")
        void missingRequiredIdIsDropped(String key) {
            Map<String, Object> event = event(bookingId, userId, advisorId);
            event.remove(key);

            consumer.onBookingCompleted(event);

            verifyNoInteractions(repository);
        }

        @Test
        @DisplayName("a null id value is dropped")
        void nullIdIsDropped() {
            Map<String, Object> event = event(bookingId, userId, advisorId);
            event.put("advisorId", null);

            consumer.onBookingCompleted(event);

            verifyNoInteractions(repository);
        }

        @Test
        @DisplayName("an entirely empty payload is dropped without throwing")
        void emptyPayloadIsDropped() {
            assertThatCode(() -> consumer.onBookingCompleted(new HashMap<>()))
                    .doesNotThrowAnyException();

            verifyNoInteractions(repository);
        }

        @Test
        @DisplayName("a null payload is dropped without throwing")
        void nullPayloadIsDropped() {
            assertThatCode(() -> consumer.onBookingCompleted(null)).doesNotThrowAnyException();

            verifyNoInteractions(repository);
        }

        /**
         * A malformed event must not even be tested for existence — the id it would be keyed by is
         * the thing that failed to parse.
         */
        @Test
        @DisplayName("a malformed event never reaches the existence check")
        void malformedEventSkipsTheExistenceCheck() {
            Map<String, Object> event = event(bookingId, userId, advisorId);
            event.put("userId", "not-a-uuid");

            consumer.onBookingCompleted(event);

            verify(repository, never()).existsById(any());
        }
    }

    // ────────────────────────────────────────────────────────────────── helpers

    private static Map<String, Object> event(UUID bookingId, UUID userId, UUID advisorId) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "BOOKING_COMPLETED");
        event.put("bookingId", bookingId.toString());
        event.put("userId", userId.toString());
        event.put("advisorId", advisorId.toString());
        return event;
    }

    private BookingCompletionRecord captureSaved() {
        ArgumentCaptor<BookingCompletionRecord> captor =
                ArgumentCaptor.forClass(BookingCompletionRecord.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }
}
