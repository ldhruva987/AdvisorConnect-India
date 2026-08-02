package com.advisorconnect.booking.application;

import com.advisorconnect.booking.adapter.in.web.dto.BookingResponse;
import com.advisorconnect.booking.adapter.in.web.dto.CreateBookingRequest;
import com.advisorconnect.booking.adapter.out.messaging.BookingEventPublisher;
import com.advisorconnect.booking.domain.model.Booking;
import com.advisorconnect.booking.domain.model.BookingStatus;
import com.advisorconnect.booking.domain.model.PaymentIntentResult;
import com.advisorconnect.booking.domain.port.out.BookingRepository;
import com.advisorconnect.booking.domain.port.out.PaymentGateway;
import com.advisorconnect.booking.domain.port.out.PaymentGatewayException;
import com.advisorconnect.booking.domain.model.UserEmailCache;
import com.advisorconnect.booking.domain.port.out.UserEmailCacheRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    private static final String DATE = "2026-08-01";

    private final UUID clientId = UUID.randomUUID();
    private final UUID advisorId = UUID.randomUUID();
    private final UUID strangerId = UUID.randomUUID();
    private final UUID bookingId = UUID.randomUUID();

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingEventPublisher eventPublisher;

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private UserEmailCacheRepository userEmailCacheRepository;

    @InjectMocks
    private BookingService bookingService;

    // ------------------------------------------------------------------ ownership on read

    @Nested
    @DisplayName("getBooking authorisation")
    class GetBookingAuthorisation {

        @Test
        @DisplayName("the client who booked the session can read it")
        void ownerCanRead() {
            given(bookingRepository.findById(bookingId)).willReturn(Optional.of(booking()));

            assertThat(bookingService.getBooking(bookingId, clientId, false).getId())
                    .isEqualTo(bookingId);
        }

        @Test
        @DisplayName("the advisor on the session can read it")
        void advisorCanRead() {
            given(bookingRepository.findById(bookingId)).willReturn(Optional.of(booking()));

            assertThat(bookingService.getBooking(bookingId, advisorId, false).getId())
                    .isEqualTo(bookingId);
        }

        @Test
        @DisplayName("an admin can read any booking")
        void adminCanRead() {
            given(bookingRepository.findById(bookingId)).willReturn(Optional.of(booking()));

            assertThat(bookingService.getBooking(bookingId, strangerId, true).getId())
                    .isEqualTo(bookingId);
        }

        @Test
        @DisplayName("an unrelated user is refused — this leaked stripePaymentIntentId before")
        void strangerIsRefused() {
            given(bookingRepository.findById(bookingId)).willReturn(Optional.of(booking()));

            assertThatThrownBy(() -> bookingService.getBooking(bookingId, strangerId, false))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("Not authorized");
        }

        @Test
        @DisplayName("a missing booking is reported as not-found, not as a permission failure")
        void missingBookingThrowsNotFound() {
            given(bookingRepository.findById(bookingId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> bookingService.getBooking(bookingId, clientId, false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Booking not found");
        }
    }

    // ------------------------------------------------------------------- slot availability

    @Nested
    @DisplayName("getAvailableSlots overlap handling")
    class Availability {

        @Test
        @DisplayName("a 60-minute booking at 10:00 blocks both 10:00 and 10:30")
        void sixtyMinuteBookingBlocksBothHalves() {
            givenBookings(confirmed(at(10, 0), 60));

            List<String> slots = bookingService.getAvailableSlots(advisorId, DATE);

            assertThat(slots).doesNotContain(iso(10, 0), iso(10, 30));
            assertThat(slots).contains(iso(9, 0), iso(9, 30), iso(11, 0), iso(11, 30));
        }

        @Test
        @DisplayName("a 30-minute booking blocks only its own slot")
        void thirtyMinuteBookingBlocksOneSlot() {
            givenBookings(confirmed(at(10, 0), 30));

            List<String> slots = bookingService.getAvailableSlots(advisorId, DATE);

            assertThat(slots).doesNotContain(iso(10, 0));
            assertThat(slots).contains(iso(10, 30));
        }

        @Test
        @DisplayName("a cancelled booking frees its slots again")
        void cancelledBookingDoesNotBlock() {
            Booking cancelled = confirmed(at(10, 0), 60);
            cancelled.setStatus(BookingStatus.CANCELLED);
            givenBookings(cancelled);

            assertThat(bookingService.getAvailableSlots(advisorId, DATE))
                    .contains(iso(10, 0), iso(10, 30));
        }

        @Test
        @DisplayName("a booking awaiting payment still holds its slot — releasing it would let a "
                + "second client book over a session mid-checkout")
        void pendingBookingStillBlocks() {
            Booking pending = confirmed(at(10, 0), 30);
            pending.setStatus(BookingStatus.PENDING);
            givenBookings(pending);

            assertThat(bookingService.getAvailableSlots(advisorId, DATE)).doesNotContain(iso(10, 0));
        }

        @Test
        @DisplayName("a booking whose payment failed releases its slot again")
        void failedPaymentFreesTheSlot() {
            Booking failed = confirmed(at(10, 0), 60);
            failed.setStatus(BookingStatus.FAILED);
            givenBookings(failed);

            assertThat(bookingService.getAvailableSlots(advisorId, DATE))
                    .contains(iso(10, 0), iso(10, 30));
        }

        @Test
        @DisplayName("legacy rows with no sessionEndDateTime still block by duration")
        void nullEndTimeFallsBackToDuration() {
            Booking legacy = confirmed(at(10, 0), 60);
            legacy.setSessionEndDateTime(null);
            givenBookings(legacy);

            assertThat(bookingService.getAvailableSlots(advisorId, DATE))
                    .doesNotContain(iso(10, 0), iso(10, 30));
        }

        @Test
        @DisplayName("an empty calendar offers every 30-minute slot from 09:00 to 17:00")
        void emptyCalendarIsFullyAvailable() {
            givenBookings();

            assertThat(bookingService.getAvailableSlots(advisorId, DATE)).hasSize(16);
        }

        private void givenBookings(Booking... bookings) {
            given(bookingRepository.findByAdvisorIdAndSessionDateTimeBetween(
                    eq(advisorId), any(Instant.class), any(Instant.class)))
                    .willReturn(List.of(bookings));
        }
    }

    // ------------------------------------------------------------------------ createBooking

    @Nested
    @DisplayName("createBooking")
    class Create {

        @Test
        @DisplayName("an unsupported duration is refused before anything is charged, persisted or published")
        void unsupportedDurationThrows() {
            CreateBookingRequest req = request(45);

            assertThatThrownBy(() -> bookingService.createBooking(req, clientId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unsupported session duration");

            verifyNoInteractions(bookingRepository);
            verifyNoInteractions(paymentGateway);
            verify(eventPublisher, never()).publishBookingCreated(any(), any(), any(), any());
        }

        @Test
        @DisplayName("a 60-minute booking stores an end time an hour after its start")
        void endTimeIsDerivedFromDuration() {
            givenPaymentIntent("pi_60", "pi_60_secret");
            givenSaveAssignsAnId();

            bookingService.createBooking(request(60), clientId);

            Booking saved = capturedBooking();
            assertThat(saved.getSessionEndDateTime())
                    .isEqualTo(saved.getSessionDateTime().plus(60, ChronoUnit.MINUTES));
            assertThat(saved.getAmountCharged()).isEqualByComparingTo(new BigDecimal("90.00"));
        }

        @Test
        @DisplayName("a 30-minute booking is charged $50 and publishes booking.created")
        void thirtyMinuteBookingIsPricedAndPublished() {
            givenPaymentIntent("pi_30", "pi_30_secret");
            givenSaveAssignsAnId();

            BookingResponse created = bookingService.createBooking(request(30), clientId);

            assertThat(created.booking().getAmountCharged())
                    .isEqualByComparingTo(new BigDecimal("50.00"));
            // No cached email for this client, so the event ships a blank address rather than
            // failing the booking; the confirmation is what degrades.
            verify(eventPublisher)
                    .publishBookingCreated(created.booking().getId(), clientId, advisorId, "");
        }

        @Test
        @DisplayName("the booking starts PENDING and carries the gateway's real payment intent id — "
                + "it used to be saved CONFIRMED with a fabricated pi_placeholder_ id")
        void bookingStartsPendingWithTheRealIntentId() {
            givenPaymentIntent("pi_3Nx9aB2eZvKYlo2C", "pi_3Nx9aB2eZvKYlo2C_secret_xyz");
            givenSaveAssignsAnId();

            BookingResponse created = bookingService.createBooking(request(30), clientId);

            Booking saved = capturedBooking();
            assertThat(saved.getStatus()).isEqualTo(BookingStatus.PENDING);
            assertThat(saved.getStripePaymentIntentId()).isEqualTo("pi_3Nx9aB2eZvKYlo2C");
            assertThat(saved.getStripePaymentIntentId()).doesNotContain("placeholder");
            assertThat(created.clientSecret()).isEqualTo("pi_3Nx9aB2eZvKYlo2C_secret_xyz");
        }

        @Test
        @DisplayName("the price charged to the gateway is the price recorded on the booking, in usd")
        void theGatewayIsChargedTheBookedPrice() {
            givenPaymentIntent("pi_60", "pi_60_secret");
            givenSaveAssignsAnId();

            bookingService.createBooking(request(60), clientId);

            verify(paymentGateway).createPaymentIntent(
                    eq(new BigDecimal("90.00")), eq("usd"), any());
        }

        @Test
        @DisplayName("payment metadata identifies the parties, so a Stripe charge can be reconciled")
        void metadataIdentifiesTheParties() {
            givenPaymentIntent("pi_30", "pi_30_secret");
            givenSaveAssignsAnId();

            bookingService.createBooking(request(30), clientId);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, String>> metadata = ArgumentCaptor.forClass(Map.class);
            verify(paymentGateway)
                    .createPaymentIntent(any(BigDecimal.class), any(String.class), metadata.capture());
            assertThat(metadata.getValue())
                    .containsEntry("userId", clientId.toString())
                    .containsEntry("advisorId", advisorId.toString());
        }

        @Test
        @DisplayName("if the charge cannot be reserved nothing is persisted and no event escapes")
        void aGatewayFailureLeavesNoBooking() {
            willThrow(new PaymentGatewayException("boom", new RuntimeException()))
                    .given(paymentGateway).createPaymentIntent(any(), any(), any());

            assertThatThrownBy(() -> bookingService.createBooking(request(30), clientId))
                    .isInstanceOf(PaymentGatewayException.class);

            verifyNoInteractions(bookingRepository);
            verifyNoInteractions(eventPublisher);
        }

        private void givenPaymentIntent(String id, String clientSecret) {
            given(paymentGateway.createPaymentIntent(any(), any(), any()))
                    .willReturn(new PaymentIntentResult(id, clientSecret));
        }

        private void givenSaveAssignsAnId() {
            given(bookingRepository.save(any(Booking.class)))
                    .willAnswer(inv -> withId(inv.getArgument(0)));
        }

        private Booking capturedBooking() {
            ArgumentCaptor<Booking> saved = ArgumentCaptor.forClass(Booking.class);
            verify(bookingRepository).save(saved.capture());
            return saved.getValue();
        }
    }

    // ------------------------------------------------------- webhook-driven status transitions

    @Nested
    @DisplayName("confirmBooking / failBooking")
    class WebhookTransitions {

        private static final String INTENT_ID = "pi_3Nx9aB2eZvKYlo2C";

        @Test
        @DisplayName("a succeeded payment moves the booking from PENDING to CONFIRMED")
        void successConfirms() {
            Booking pending = pending();
            given(bookingRepository.findByStripePaymentIntentId(INTENT_ID))
                    .willReturn(Optional.of(pending));

            bookingService.confirmBooking(INTENT_ID);

            assertThat(pending.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
            verify(bookingRepository).save(pending);
        }

        @Test
        @DisplayName("a failed payment moves the booking to FAILED, not CANCELLED — nobody withdrew")
        void failureMarksFailed() {
            Booking pending = pending();
            given(bookingRepository.findByStripePaymentIntentId(INTENT_ID))
                    .willReturn(Optional.of(pending));

            bookingService.failBooking(INTENT_ID);

            assertThat(pending.getStatus()).isEqualTo(BookingStatus.FAILED);
            verify(bookingRepository).save(pending);
        }

        @Test
        @DisplayName("a redelivered confirmation is a no-op — Stripe retries until it sees a 2xx")
        void confirmationIsIdempotent() {
            Booking alreadyConfirmed = pending();
            alreadyConfirmed.setStatus(BookingStatus.CONFIRMED);
            given(bookingRepository.findByStripePaymentIntentId(INTENT_ID))
                    .willReturn(Optional.of(alreadyConfirmed));

            bookingService.confirmBooking(INTENT_ID);

            verify(bookingRepository, never()).save(any());
        }

        @Test
        @DisplayName("a payment failure arriving after the user cancelled does not overwrite CANCELLED")
        void failureDoesNotOverwriteANonPendingStatus() {
            Booking cancelled = pending();
            cancelled.setStatus(BookingStatus.CANCELLED);
            given(bookingRepository.findByStripePaymentIntentId(INTENT_ID))
                    .willReturn(Optional.of(cancelled));

            bookingService.failBooking(INTENT_ID);

            assertThat(cancelled.getStatus()).isEqualTo(BookingStatus.CANCELLED);
            verify(bookingRepository, never()).save(any());
        }

        @Test
        @DisplayName("an unknown payment intent is ignored rather than thrown on, so Stripe stops retrying")
        void unknownIntentIsIgnored() {
            given(bookingRepository.findByStripePaymentIntentId(INTENT_ID))
                    .willReturn(Optional.empty());

            bookingService.confirmBooking(INTENT_ID);
            bookingService.failBooking(INTENT_ID);

            verify(bookingRepository, never()).save(any());
        }

        private Booking pending() {
            Booking b = confirmed(at(10, 0), 30);
            b.setStatus(BookingStatus.PENDING);
            b.setStripePaymentIntentId(INTENT_ID);
            return b;
        }
    }

    // ------------------------------------------------------------------------------ cancel

    @Nested
    @DisplayName("cancelBooking")
    class Cancel {

        @Test
        @DisplayName("the owner can cancel, and booking.cancelled is published")
        void ownerCanCancel() {
            Booking booking = booking();
            given(bookingRepository.findById(bookingId)).willReturn(Optional.of(booking));

            bookingService.cancelBooking(bookingId, clientId);

            assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
            verify(eventPublisher).publishBookingCancelled(bookingId, clientId, "");
        }

        @Test
        @DisplayName("a non-owner cannot cancel — including the advisor on the session")
        void advisorCannotCancel() {
            given(bookingRepository.findById(bookingId)).willReturn(Optional.of(booking()));

            assertThatThrownBy(() -> bookingService.cancelBooking(bookingId, advisorId))
                    .isInstanceOf(SecurityException.class);

            verify(bookingRepository, never()).save(any());
        }
    }

    // -------------------------------------------------------------------------- my bookings

    @Test
    @DisplayName("getMyBookings returns only the caller's bookings")
    void myBookingsIsScopedToCaller() {
        given(bookingRepository.findByUserId(clientId)).willReturn(List.of(booking()));

        assertThat(bookingService.getMyBookings(clientId))
                .singleElement()
                .satisfies(b -> assertThat(b.getUserId()).isEqualTo(clientId));
    }

    // ----------------------------------------------------------------- userEmail on events

    /**
     * Booking events carried no {@code userEmail} at all, and notification-service returns
     * without sending when that field is blank — so no booking email was ever delivered. The
     * address now comes from the local {@code user.registered} projection.
     */
    @Nested
    @DisplayName("the client's email is attached to booking events")
    class UserEmailPropagation {

        private static final String EMAIL = "client@example.com";

        @Test
        @DisplayName("booking.created carries the cached email")
        void createdCarriesTheCachedEmail() {
            givenCachedEmail(EMAIL);
            givenPaymentIntent("pi_email", "pi_email_secret");
            givenSaveAssignsAnId();

            BookingResponse created = bookingService.createBooking(request(30), clientId);

            verify(eventPublisher).publishBookingCreated(
                    created.booking().getId(), clientId, advisorId, EMAIL);
        }

        @Test
        @DisplayName("booking.cancelled carries the cached email")
        void cancelledCarriesTheCachedEmail() {
            givenCachedEmail(EMAIL);
            given(bookingRepository.findById(bookingId)).willReturn(Optional.of(booking()));

            bookingService.cancelBooking(bookingId, clientId);

            verify(eventPublisher).publishBookingCancelled(bookingId, clientId, EMAIL);
        }

        @Test
        @DisplayName("a projection that has not caught up yet still lets the booking through")
        void cacheMissDoesNotBlockTheBooking() {
            // Default Mockito behaviour for an Optional-returning method is Optional.empty(),
            // i.e. the user registered a moment ago and the event has not been consumed yet.
            givenPaymentIntent("pi_nocache", "pi_nocache_secret");
            givenSaveAssignsAnId();

            BookingResponse created = bookingService.createBooking(request(30), clientId);

            assertThat(created.booking()).isNotNull();
            verify(eventPublisher).publishBookingCreated(
                    created.booking().getId(), clientId, advisorId, "");
        }

        private void givenCachedEmail(String email) {
            given(userEmailCacheRepository.findById(clientId)).willReturn(
                    Optional.of(UserEmailCache.builder().id(clientId).email(email).build()));
        }

        private void givenPaymentIntent(String id, String clientSecret) {
            given(paymentGateway.createPaymentIntent(any(), any(), any()))
                    .willReturn(new PaymentIntentResult(id, clientSecret));
        }

        private void givenSaveAssignsAnId() {
            given(bookingRepository.save(any(Booking.class)))
                    .willAnswer(inv -> withId(inv.getArgument(0)));
        }
    }

    // ------------------------------------------------------------------------------ helpers

    private Booking booking() {
        return confirmed(at(10, 0), 30);
    }

    private Booking confirmed(Instant start, int durationMinutes) {
        return Booking.builder()
                .id(bookingId)
                .userId(clientId)
                .advisorId(advisorId)
                .sessionDateTime(start)
                .sessionEndDateTime(start.plus(durationMinutes, ChronoUnit.MINUTES))
                .durationMinutes(durationMinutes)
                .amountCharged(new BigDecimal("50.00"))
                .status(BookingStatus.CONFIRMED)
                .stripePaymentIntentId("pi_test")
                .build();
    }

    private CreateBookingRequest request(int durationMinutes) {
        CreateBookingRequest req = new CreateBookingRequest();
        req.setAdvisorId(advisorId);
        req.setSessionDateTime(at(10, 0));
        req.setDurationMinutes(durationMinutes);
        req.setStripePaymentMethodId("pm_test");
        return req;
    }

    private static Booking withId(Booking b) {
        if (b.getId() == null) {
            b.setId(UUID.randomUUID());
        }
        return b;
    }

    private static Instant at(int hour, int minute) {
        return LocalDate.parse(DATE).atTime(hour, minute).toInstant(ZoneOffset.UTC);
    }

    private static String iso(int hour, int minute) {
        return at(hour, minute).toString();
    }
}
