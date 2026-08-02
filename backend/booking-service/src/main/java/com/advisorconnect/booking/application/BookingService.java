package com.advisorconnect.booking.application;

import com.advisorconnect.booking.adapter.in.web.dto.BookingResponse;
import com.advisorconnect.booking.adapter.in.web.dto.CreateBookingRequest;
import com.advisorconnect.booking.adapter.out.messaging.BookingEventPublisher;
import com.advisorconnect.booking.domain.model.Booking;
import com.advisorconnect.booking.domain.model.BookingConflictException;
import com.advisorconnect.booking.domain.model.BookingStatus;
import com.advisorconnect.booking.domain.model.PaymentIntentResult;
import com.advisorconnect.booking.domain.model.PricingPolicy;
import com.advisorconnect.booking.domain.model.UserEmailCache;
import com.advisorconnect.booking.domain.port.out.BookingRepository;
import com.advisorconnect.booking.domain.port.out.PaymentGateway;
import com.advisorconnect.booking.domain.port.out.UserEmailCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class BookingService {

    /** Working day covered by the availability calendar, in UTC. */
    private static final LocalTime DAY_START = LocalTime.of(9, 0);
    private static final LocalTime DAY_END = LocalTime.of(17, 0);
    private static final int SLOT_MINUTES = 30;

    /** Every session is priced and charged in US dollars; there is no multi-currency product. */
    private static final String CURRENCY = "usd";

    private final BookingRepository bookingRepository;
    private final BookingEventPublisher eventPublisher;
    private final PaymentGateway paymentGateway;
    private final UserEmailCacheRepository userEmailCacheRepository;

    /**
     * Reserves a real charge with the payment gateway and records the booking as
     * {@link BookingStatus#PENDING}.
     *
     * <p>This used to invent {@code "pi_placeholder_" + randomUUID()} and save the booking as
     * {@code CONFIRMED} — so an advisor's calendar filled with sessions nobody had paid for, and
     * the stored payment intent id matched nothing in Stripe. A booking is now only confirmed by
     * {@link #confirmBooking(String)}, driven by Stripe's {@code payment_intent.succeeded}
     * webhook.
     *
     * <p>The gateway is called before the row is written: if the charge cannot even be reserved,
     * nothing is persisted and no {@code booking.created} event is emitted.
     *
     * <p>The advisor's calendar is re-checked for an overlap right before charging anything. This
     * used to trust the client's earlier {@link #getAvailableSlots} read: nothing stopped two
     * concurrent requests, or a client that simply ignored the availability list, from both
     * creating and charging a booking for the same advisor/time slot.
     *
     * @throws BookingConflictException if the advisor already holds an overlapping booking
     * @return the persisted booking plus the client secret the browser needs to confirm payment
     */
    public BookingResponse createBooking(CreateBookingRequest req, UUID userId) {
        // Request validation already rejects anything other than 30/60; this is the
        // defence-in-depth layer for callers that do not go through the web layer.
        BigDecimal amount = PricingPolicy.priceFor(req.getDurationMinutes());

        Instant start = req.getSessionDateTime();
        Instant end = start.plus(req.getDurationMinutes(), ChronoUnit.MINUTES);

        if (hasOverlap(req.getAdvisorId(), start, end)) {
            throw new BookingConflictException(
                    "The advisor already has a booking that overlaps this time slot");
        }

        // The booking id does not exist yet — it is generated on save — so reconciliation
        // metadata carries the participants and the slot instead. Stripe requires string values.
        Map<String, String> metadata = Map.of(
                "userId", userId.toString(),
                "advisorId", req.getAdvisorId().toString(),
                "sessionDateTime", start.toString(),
                "durationMinutes", String.valueOf(req.getDurationMinutes())
        );

        PaymentIntentResult intent = paymentGateway.createPaymentIntent(amount, CURRENCY, metadata);

        Booking booking = Booking.builder()
                .userId(userId)
                .advisorId(req.getAdvisorId())
                .sessionDateTime(start)
                .sessionEndDateTime(end)
                .durationMinutes(req.getDurationMinutes())
                .amountCharged(amount)
                .status(BookingStatus.PENDING)
                .stripePaymentIntentId(intent.paymentIntentId())
                .build();

        booking = bookingRepository.save(booking);

        eventPublisher.publishBookingCreated(
                booking.getId(), userId, req.getAdvisorId(), lookupEmail(userId));

        log.info("Booking created (pending payment): id={} userId={} advisorId={} paymentIntentId={}",
                booking.getId(), userId, req.getAdvisorId(), intent.paymentIntentId());
        return new BookingResponse(booking, intent.clientSecret());
    }

    /**
     * Marks a booking paid. Called only from the Stripe webhook, which is the single source of
     * truth for whether money actually moved.
     *
     * <p>Idempotent and forgiving by design: Stripe redelivers webhooks until it gets a 2xx, and
     * an intent the service has never heard of (a payment created outside this flow, or a booking
     * already deleted) must not make the endpoint fail forever. Both cases are logged and
     * swallowed so the delivery is acknowledged rather than retried indefinitely.
     */
    public void confirmBooking(String paymentIntentId) {
        Optional<Booking> found = bookingRepository.findByStripePaymentIntentId(paymentIntentId);
        if (found.isEmpty()) {
            log.warn("payment_intent.succeeded for unknown paymentIntentId={} — ignoring", paymentIntentId);
            return;
        }
        Booking booking = found.get();
        if (booking.getStatus() != BookingStatus.PENDING) {
            log.info("Booking id={} already in status {} — ignoring duplicate confirmation",
                    booking.getId(), booking.getStatus());
            return;
        }
        booking.setStatus(BookingStatus.CONFIRMED);
        bookingRepository.save(booking);
        log.info("Booking confirmed by Stripe webhook: id={} paymentIntentId={}",
                booking.getId(), paymentIntentId);
    }

    /**
     * Marks a booking's payment as failed. Same idempotency and unknown-intent handling as
     * {@link #confirmBooking(String)}.
     *
     * <p>{@link BookingStatus#FAILED} rather than {@code CANCELLED}: the user did not withdraw,
     * their card did.
     */
    public void failBooking(String paymentIntentId) {
        Optional<Booking> found = bookingRepository.findByStripePaymentIntentId(paymentIntentId);
        if (found.isEmpty()) {
            log.warn("payment_intent.payment_failed for unknown paymentIntentId={} — ignoring", paymentIntentId);
            return;
        }
        Booking booking = found.get();
        if (booking.getStatus() != BookingStatus.PENDING) {
            log.info("Booking id={} already in status {} — ignoring payment failure",
                    booking.getId(), booking.getStatus());
            return;
        }
        booking.setStatus(BookingStatus.FAILED);
        bookingRepository.save(booking);
        log.info("Booking payment failed: id={} paymentIntentId={}", booking.getId(), paymentIntentId);
    }

    /**
     * Fetches a booking, enforcing that the caller is entitled to see it.
     *
     * <p>There used to be no check here at all: any authenticated user could read any
     * booking by id, including its {@code stripePaymentIntentId}.
     *
     * <p>Visibility is deliberately wider than "the user who booked it" — the advisor on the
     * session needs to see their own bookings, and admins need to see all of them.
     *
     * @throws SecurityException if the caller is neither party to the booking nor an admin.
     *                           Same exception type {@link #cancelBooking} already throws for
     *                           its ownership check, so both failures surface identically.
     */
    @Transactional(readOnly = true)
    public Booking getBooking(UUID id, UUID requesterId, boolean isAdmin) {
        Booking booking = findOrThrow(id);
        boolean permitted = isAdmin
                || booking.getUserId().equals(requesterId)
                || booking.getAdvisorId().equals(requesterId);
        if (!permitted) {
            throw new SecurityException("Not authorized to view this booking");
        }
        return booking;
    }

    /** Every booking the caller made. Scoped to the caller by construction — no id is accepted. */
    @Transactional(readOnly = true)
    public List<Booking> getMyBookings(UUID userId) {
        return bookingRepository.findByUserId(userId);
    }

    public void cancelBooking(UUID id, UUID userId) {
        Booking booking = findOrThrow(id);
        if (!booking.getUserId().equals(userId)) {
            throw new SecurityException("Not authorized to cancel this booking");
        }
        if (booking.getStatus() == BookingStatus.COMPLETED) {
            throw new IllegalStateException("Cannot cancel a completed booking");
        }
        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);

        eventPublisher.publishBookingCancelled(id, userId, lookupEmail(userId));
    }

    /**
     * The client's email from the local {@code user.registered} projection, or {@code ""} when
     * the projection has not caught up.
     *
     * <p>Never throws and never blocks the booking. The projection is eventually consistent with
     * auth-service, so a user who registers and books within the same second can legitimately
     * have no row yet; the event then ships with a blank {@code userEmail} and
     * notification-service skips the email rather than failing. Losing one confirmation email is
     * a far better outcome than failing the booking that was paid for.
     */
    private String lookupEmail(UUID userId) {
        return userEmailCacheRepository.findById(userId)
                .map(UserEmailCache::getEmail)
                .orElseGet(() -> {
                    log.warn("No cached email for userId={} — booking event will ship without one",
                            userId);
                    return "";
                });
    }

    /**
     * Open 30-minute slots for an advisor on a given UTC date.
     *
     * <p>Availability is computed by interval overlap, not by matching a slot's start against
     * a booked start. The old exact-match approach meant a 60-minute booking at 10:00 left
     * 10:30 bookable, so two clients could be sold overlapping halves of the same session.
     */
    @Transactional(readOnly = true)
    public List<String> getAvailableSlots(UUID advisorId, String date) {
        LocalDate localDate = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE);
        Instant from = localDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = localDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<Booking> existing = bookingRepository
                .findByAdvisorIdAndSessionDateTimeBetween(advisorId, from, to);

        // Half-open [start, end) intervals occupied by live bookings.
        List<Interval> booked = new ArrayList<>();
        for (Booking b : existing) {
            if (holdsTheSlot(b)) {
                booked.add(new Interval(b.getSessionDateTime(), endOf(b)));
            }
        }

        List<String> available = new ArrayList<>();
        LocalTime cursor = DAY_START;
        while (cursor.isBefore(DAY_END)) {
            Instant slotStart = localDate.atTime(cursor).toInstant(ZoneOffset.UTC);
            Instant slotEnd = slotStart.plus(SLOT_MINUTES, ChronoUnit.MINUTES);
            if (booked.stream().noneMatch(i -> i.overlaps(slotStart, slotEnd))) {
                available.add(slotStart.toString());
            }
            cursor = cursor.plusMinutes(SLOT_MINUTES);
        }
        return available;
    }

    /**
     * Whether the advisor already holds a live booking overlapping {@code [start, end)}.
     *
     * <p>Reuses the same day-window query and half-open-interval overlap test that
     * {@link #getAvailableSlots} already relies on, rather than a new repository query: sessions
     * only ever run within {@link #DAY_START}–{@link #DAY_END} on a single UTC day, so the
     * existing day-boundary fetch already covers every booking that could overlap {@code start}.
     */
    private boolean hasOverlap(UUID advisorId, Instant start, Instant end) {
        LocalDate day = start.atZone(ZoneOffset.UTC).toLocalDate();
        Instant dayStart = day.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant dayEnd = day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        return bookingRepository.findByAdvisorIdAndSessionDateTimeBetween(advisorId, dayStart, dayEnd)
                .stream()
                .filter(BookingService::holdsTheSlot)
                .anyMatch(existing -> new Interval(existing.getSessionDateTime(), endOf(existing))
                        .overlaps(start, end));
    }

    /**
     * Whether a booking still occupies its slot on the advisor's calendar.
     *
     * <p>{@code PENDING} counts: the payment intent is open and the browser is mid-confirmation,
     * so releasing the slot would let a second client book over a session that is about to be
     * paid for. {@code FAILED} does not — a declined card must hand the time back rather than
     * hold it forever, which is the whole reason it is a separate status from {@code CANCELLED}.
     */
    private static boolean holdsTheSlot(Booking b) {
        return b.getStatus() != BookingStatus.CANCELLED && b.getStatus() != BookingStatus.FAILED;
    }

    private Booking findOrThrow(UUID id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + id));
    }

    /**
     * End of a booking, tolerating rows persisted before {@code sessionEndDateTime} existed
     * (the column is additive, so historical rows carry null).
     */
    private static Instant endOf(Booking b) {
        return b.getSessionEndDateTime() != null
                ? b.getSessionEndDateTime()
                : b.getSessionDateTime().plus(b.getDurationMinutes(), ChronoUnit.MINUTES);
    }

    /** Half-open time interval {@code [start, end)}. */
    private record Interval(Instant start, Instant end) {
        boolean overlaps(Instant otherStart, Instant otherEnd) {
            return otherStart.isBefore(end) && start.isBefore(otherEnd);
        }
    }
}
