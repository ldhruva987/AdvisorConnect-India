package com.advisorconnect.notification.adapter.in.messaging;

import com.advisorconnect.notification.application.EmailNotificationService;
import com.advisorconnect.notification.application.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Every consumed event must produce both a persisted notification and an email attempt.
 *
 * <p>Before this, the consumer only sent mail — so with SMTP unconfigured, or an event carrying
 * no address, nothing whatsoever was recorded and the user could not find out what had happened.
 */
@ExtendWith(MockitoExtension.class)
class BookingEventConsumerTest {

    private final UUID userId = UUID.randomUUID();

    @Mock
    private EmailNotificationService emailService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private BookingEventConsumer consumer;

    // ------------------------------------------------------ both sides fire, per event type

    @Test
    @DisplayName("booking.created persists a notification and attempts the confirmation email")
    void bookingCreated() {
        Map<String, Object> event = bookingEvent("BOOKING_CREATED");

        consumer.onBookingCreated(event);

        verify(notificationService).create(
                eq(userId), eq("BOOKING_CONFIRMED"), eq("Booking confirmed"), anyString());
        verify(emailService).sendBookingConfirmation(event);
    }

    @Test
    @DisplayName("booking.cancelled persists a notification and attempts the cancellation email")
    void bookingCancelled() {
        Map<String, Object> event = bookingEvent("BOOKING_CANCELLED");

        consumer.onBookingCancelled(event);

        verify(notificationService).create(
                eq(userId), eq("BOOKING_CANCELLED"), eq("Booking cancelled"), anyString());
        verify(emailService).sendBookingCancellation(event);
    }

    @Test
    @DisplayName("advisor.approved persists a notification against the advisor's user id")
    void advisorApproved() {
        // AdvisorEventPublisher keys the event by advisorId, which is the auth-service user id.
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "ADVISOR_APPROVED");
        event.put("advisorId", userId.toString());
        event.put("email", "advisor@example.com");

        consumer.onAdvisorApproved(event);

        verify(notificationService).create(
                eq(userId), eq("ADVISOR_APPROVED"), eq("Application approved"), anyString());
        verify(emailService).sendAdvisorApprovalNotification(event);
    }

    @Test
    @DisplayName("advisor.rejected persists a notification carrying the reason")
    void advisorRejected() {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "ADVISOR_REJECTED");
        event.put("userId", userId.toString());
        event.put("reason", "Insufficient documentation");
        event.put("email", "advisor@example.com");

        consumer.onAdvisorRejected(event);

        verify(notificationService).create(userId, "ADVISOR_REJECTED", "Application not approved",
                "We are unable to approve your advisor application at this time. "
                        + "Reason: Insufficient documentation");
        verify(emailService).sendAdvisorRejectionNotification(event);
    }

    // ------------------------------------------------------------------- degraded payloads

    @Test
    @DisplayName("an event with no usable recipient id still attempts the email, and does not throw")
    void unaddressableEventIsNotAPoisonPill() {
        // advisor.rejected as advisor-service currently emits it: an applicationId, which is not
        // a user id, so there is nobody to file the notification under.
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "ADVISOR_REJECTED");
        event.put("applicationId", UUID.randomUUID().toString());
        event.put("email", "advisor@example.com");

        assertThatCode(() -> consumer.onAdvisorRejected(event)).doesNotThrowAnyException();

        verifyNoInteractions(notificationService);
        verify(emailService).sendAdvisorRejectionNotification(event);
    }

    @Test
    @DisplayName("a non-UUID recipient id is ignored rather than redelivered forever")
    void malformedRecipientIdIsIgnored() {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "BOOKING_CREATED");
        event.put("userId", "not-a-uuid");

        assertThatCode(() -> consumer.onBookingCreated(event)).doesNotThrowAnyException();

        verifyNoInteractions(notificationService);
        verify(emailService).sendBookingConfirmation(event);
    }

    @Test
    @DisplayName("a failed database write does not stop the email or poison the partition")
    void persistenceFailureIsContained() {
        willThrow(new DataIntegrityViolationException("column too long"))
                .given(notificationService).create(any(), any(), any(), any());
        Map<String, Object> event = bookingEvent("BOOKING_CREATED");

        assertThatCode(() -> consumer.onBookingCreated(event)).doesNotThrowAnyException();

        verify(emailService).sendBookingConfirmation(event);
    }

    // -------------------------------------------------------------------------------- helpers

    /** The payload booking-service's {@code BookingEventPublisher} now sends. */
    private Map<String, Object> bookingEvent(String eventType) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", eventType);
        event.put("bookingId", UUID.randomUUID().toString());
        event.put("userId", userId.toString());
        event.put("userEmail", "client@example.com");
        return event;
    }
}
