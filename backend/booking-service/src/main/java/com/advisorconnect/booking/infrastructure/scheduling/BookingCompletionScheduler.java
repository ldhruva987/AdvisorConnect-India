package com.advisorconnect.booking.infrastructure.scheduling;

import com.advisorconnect.booking.adapter.out.messaging.BookingEventPublisher;
import com.advisorconnect.booking.domain.model.Booking;
import com.advisorconnect.booking.domain.model.BookingStatus;
import com.advisorconnect.booking.domain.model.UserEmailCache;
import com.advisorconnect.booking.domain.port.out.BookingRepository;
import com.advisorconnect.booking.domain.port.out.UserEmailCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Moves finished sessions from {@code CONFIRMED} to {@code COMPLETED}.
 *
 * <p>Nothing in the service ever performed this transition: a booking stayed {@code CONFIRMED}
 * forever once paid, so {@code completedAt} was never set and no downstream feature could tell a
 * session that has happened from one still in the future. Reviews in particular can only be
 * offered for a completed session.
 *
 * <p>A periodic sweep rather than an event: nothing happens <em>at</em> the end of a session for
 * the system to react to, so the passage of time has to be polled. Five minutes is fine enough
 * granularity for a "your session is over" signal.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BookingCompletionScheduler {

    /** Five minutes. */
    private static final long INTERVAL_MS = 300_000L;

    private final BookingRepository bookingRepository;
    private final BookingEventPublisher eventPublisher;
    private final UserEmailCacheRepository userEmailCacheRepository;

    /**
     * Deliberately <em>not</em> {@code @Transactional} over the whole sweep. Each
     * {@code save} runs in its own transaction (Spring Data's own), which is what makes the
     * per-booking {@code catch} below meaningful — inside one enclosing transaction a failed
     * save would mark it rollback-only and quietly discard every sibling completion the sweep
     * had already done.
     */
    @Scheduled(fixedRate = INTERVAL_MS)
    public void completeFinishedSessions() {
        Instant now = Instant.now();
        List<Booking> due = bookingRepository
                .findByStatusAndSessionEndDateTimeBefore(BookingStatus.CONFIRMED, now);

        if (due.isEmpty()) {
            return;
        }

        int completed = 0;
        for (Booking booking : due) {
            try {
                booking.setStatus(BookingStatus.COMPLETED);
                booking.setCompletedAt(Instant.now());
                bookingRepository.save(booking);
                eventPublisher.publishBookingCompleted(
                        booking.getId(), booking.getUserId(), booking.getAdvisorId(),
                        lookupEmail(booking.getUserId()), booking.getAmountCharged());
                completed++;
            } catch (RuntimeException e) {
                // One bad row — an unreachable broker, a stale version — must not strand every
                // other finished session until the next sweep. The next run retries it, because
                // it is only picked up while still CONFIRMED.
                log.error("Failed to complete booking id={}: {}", booking.getId(), e.getMessage(), e);
            }
        }

        log.info("Booking completion sweep: {} of {} due sessions marked COMPLETED", completed, due.size());
    }

    /**
     * The client's email from the local {@code user.registered} projection, or {@code ""} when
     * the projection has no row. Blank is a valid value on the wire — notification-service skips
     * the email rather than failing — so a cache miss must not abort the completion.
     */
    private String lookupEmail(UUID userId) {
        return userEmailCacheRepository.findById(userId)
                .map(UserEmailCache::getEmail)
                .orElse("");
    }
}
