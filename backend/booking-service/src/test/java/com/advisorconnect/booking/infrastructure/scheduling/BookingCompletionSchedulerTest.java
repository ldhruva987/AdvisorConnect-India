package com.advisorconnect.booking.infrastructure.scheduling;

import com.advisorconnect.booking.adapter.out.messaging.BookingEventPublisher;
import com.advisorconnect.booking.domain.model.Booking;
import com.advisorconnect.booking.domain.model.BookingStatus;
import com.advisorconnect.booking.domain.port.out.BookingRepository;
import com.advisorconnect.booking.domain.port.out.UserEmailCacheRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * The CONFIRMED → COMPLETED sweep.
 *
 * <p>The repository is stubbed with the <em>real</em> predicate rather than a canned list, so the
 * test proves the scheduler asks the right question — a sweep that queried for the wrong status,
 * or compared against the wrong instant, would pick up bookings it must not touch and fail here.
 */
@ExtendWith(MockitoExtension.class)
class BookingCompletionSchedulerTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingEventPublisher eventPublisher;

    @Mock
    private UserEmailCacheRepository userEmailCacheRepository;

    private BookingCompletionScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new BookingCompletionScheduler(
                bookingRepository, eventPublisher, userEmailCacheRepository);
    }

    // ---------------------------------------------------------------------- what gets swept

    @Test
    @DisplayName("a confirmed session whose end time has passed becomes COMPLETED with completedAt set")
    void finishedConfirmedSessionIsCompleted() {
        Booking finished = booking(BookingStatus.CONFIRMED, minutesFromNow(-90));
        givenCalendar(finished);

        Instant before = Instant.now();
        scheduler.completeFinishedSessions();
        Instant after = Instant.now();

        assertThat(finished.getStatus()).isEqualTo(BookingStatus.COMPLETED);
        assertThat(finished.getCompletedAt()).isBetween(before, after);
        verify(bookingRepository).save(finished);
        verify(eventPublisher).publishBookingCompleted(
                finished.getId(), finished.getUserId(), finished.getAdvisorId(), "",
                // The amount travels with the event: admin-service's revenue total is built from
                // this field alone, so a sweep that dropped it would leave the dashboard at ₹0.00.
                new BigDecimal("500.00"));
    }

    @Test
    @DisplayName("a confirmed session still in progress is left alone")
    void sessionStillRunningIsUntouched() {
        Booking inProgress = booking(BookingStatus.CONFIRMED, minutesFromNow(30));
        givenCalendar(inProgress);

        scheduler.completeFinishedSessions();

        assertThat(inProgress.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(inProgress.getCompletedAt()).isNull();
        verify(bookingRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("past sessions that were never confirmed are left alone, whatever their status")
    void unconfirmedPastSessionsAreUntouched() {
        Booking pending = booking(BookingStatus.PENDING, minutesFromNow(-90));
        Booking cancelled = booking(BookingStatus.CANCELLED, minutesFromNow(-90));
        Booking failed = booking(BookingStatus.FAILED, minutesFromNow(-90));
        Booking alreadyDone = booking(BookingStatus.COMPLETED, minutesFromNow(-90));
        givenCalendar(pending, cancelled, failed, alreadyDone);

        scheduler.completeFinishedSessions();

        assertThat(List.of(pending, cancelled, failed, alreadyDone))
                .extracting(Booking::getStatus)
                .containsExactly(BookingStatus.PENDING, BookingStatus.CANCELLED,
                        BookingStatus.FAILED, BookingStatus.COMPLETED);
        verify(bookingRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("a mixed calendar completes exactly the finished confirmed sessions, one event each")
    void onlyTheDueOnesAreCompleted() {
        Booking finishedA = booking(BookingStatus.CONFIRMED, minutesFromNow(-120));
        Booking finishedB = booking(BookingStatus.CONFIRMED, minutesFromNow(-5));
        Booking future = booking(BookingStatus.CONFIRMED, minutesFromNow(120));
        Booking pendingPast = booking(BookingStatus.PENDING, minutesFromNow(-120));
        givenCalendar(finishedA, finishedB, future, pendingPast);

        scheduler.completeFinishedSessions();

        assertThat(finishedA.getStatus()).isEqualTo(BookingStatus.COMPLETED);
        assertThat(finishedB.getStatus()).isEqualTo(BookingStatus.COMPLETED);
        assertThat(future.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(pendingPast.getStatus()).isEqualTo(BookingStatus.PENDING);

        verify(eventPublisher).publishBookingCompleted(
                finishedA.getId(), finishedA.getUserId(), finishedA.getAdvisorId(), "",
                finishedA.getAmountCharged());
        verify(eventPublisher).publishBookingCompleted(
                finishedB.getId(), finishedB.getUserId(), finishedB.getAdvisorId(), "",
                finishedB.getAmountCharged());
        // Exactly one event per transitioned booking, and none for the two left behind.
        verify(eventPublisher, org.mockito.Mockito.times(2))
                .publishBookingCompleted(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("legacy rows with no end time are never completed — the query cannot match a null")
    void rowsWithoutAnEndTimeAreSkipped() {
        Booking legacy = booking(BookingStatus.CONFIRMED, minutesFromNow(-120));
        legacy.setSessionEndDateTime(null);
        givenCalendar(legacy);

        scheduler.completeFinishedSessions();

        assertThat(legacy.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("an empty sweep saves and publishes nothing")
    void emptySweepDoesNothing() {
        givenCalendar();

        scheduler.completeFinishedSessions();

        verify(bookingRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    // -------------------------------------------------------------------------- resilience

    @Test
    @DisplayName("one failing booking does not strand the rest of the sweep")
    void oneFailureDoesNotAbortTheSweep() {
        Booking doomed = booking(BookingStatus.CONFIRMED, minutesFromNow(-120));
        Booking healthy = booking(BookingStatus.CONFIRMED, minutesFromNow(-60));
        givenCalendar(doomed, healthy);
        willThrow(new RuntimeException("row is gone")).given(bookingRepository).save(doomed);

        scheduler.completeFinishedSessions();

        verify(bookingRepository).save(healthy);
        verify(eventPublisher).publishBookingCompleted(
                healthy.getId(), healthy.getUserId(), healthy.getAdvisorId(), "",
                healthy.getAmountCharged());
        // The doomed booking produced no event, and stays CONFIRMED in the database, so the
        // next sweep picks it up again.
        verify(eventPublisher, never()).publishBookingCompleted(
                eq(doomed.getId()), any(), any(), any(), any());
    }

    // ------------------------------------------------------------------------------ helpers

    /**
     * Stubs the repository with the query's real semantics: bookings in the requested status
     * whose {@code sessionEndDateTime} is non-null and strictly before the cutoff. A null end
     * time never matches, exactly as {@code sessionEndDateTime < ?} behaves in SQL.
     */
    private void givenCalendar(Booking... calendar) {
        List<Booking> all = new ArrayList<>(Arrays.asList(calendar));
        given(bookingRepository.findByStatusAndSessionEndDateTimeBefore(any(), any()))
                .willAnswer(invocation -> {
                    BookingStatus status = invocation.getArgument(0);
                    Instant cutoff = invocation.getArgument(1);
                    return all.stream()
                            .filter(b -> b.getStatus() == status)
                            .filter(b -> b.getSessionEndDateTime() != null)
                            .filter(b -> b.getSessionEndDateTime().isBefore(cutoff))
                            .toList();
                });
    }

    private static Instant minutesFromNow(long minutes) {
        return Instant.now().plus(minutes, ChronoUnit.MINUTES);
    }

    private static Booking booking(BookingStatus status, Instant end) {
        Instant start = end.minus(30, ChronoUnit.MINUTES);
        return Booking.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .advisorId(UUID.randomUUID())
                .sessionDateTime(start)
                .sessionEndDateTime(end)
                .durationMinutes(30)
                .amountCharged(new BigDecimal("500.00"))
                .status(status)
                .razorpayOrderId("order_" + UUID.randomUUID())
                .build();
    }
}
