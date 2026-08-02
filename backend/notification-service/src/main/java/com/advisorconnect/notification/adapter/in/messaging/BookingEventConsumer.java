package com.advisorconnect.notification.adapter.in.messaging;

import com.advisorconnect.notification.application.EmailNotificationService;
import com.advisorconnect.notification.application.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Turns platform events into a persisted notification and a best-effort email, in that order.
 *
 * <p>This used to only send mail. If SMTP was unconfigured — which it is by default — or the
 * event carried no address, the event vanished without trace and the user had no way to learn
 * what had happened. Every handler now records the notification first, so the inbox is
 * complete regardless of whether delivery succeeds.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BookingEventConsumer {

    private final EmailNotificationService emailService;
    private final NotificationService notificationService;

    @KafkaListener(topics = "booking.created", groupId = "notification-service")
    public void onBookingCreated(Map<String, Object> event) {
        log.info("Received booking.created event: {}", event);
        record(event, "BOOKING_CONFIRMED", "Booking confirmed",
                "Your video session has been booked. Check your calendar for details.");
        emailService.sendBookingConfirmation(event);
    }

    @KafkaListener(topics = "booking.cancelled", groupId = "notification-service")
    public void onBookingCancelled(Map<String, Object> event) {
        log.info("Received booking.cancelled event: {}", event);
        record(event, "BOOKING_CANCELLED", "Booking cancelled",
                "Your booking has been cancelled. Any refund is initiated within 5-10 business days.");
        emailService.sendBookingCancellation(event);
    }

    @KafkaListener(topics = "advisor.approved", groupId = "notification-service")
    public void onAdvisorApproved(Map<String, Object> event) {
        log.info("Received advisor.approved event: {}", event);
        record(event, "ADVISOR_APPROVED", "Application approved",
                "Congratulations! Your advisor application has been approved and you are now live "
                        + "on the platform.");
        emailService.sendAdvisorApprovalNotification(event);
    }

    @KafkaListener(topics = "advisor.rejected", groupId = "notification-service")
    public void onAdvisorRejected(Map<String, Object> event) {
        log.info("Received advisor.rejected event: {}", event);
        String reason = string(event, "reason");
        record(event, "ADVISOR_REJECTED", "Application not approved",
                "We are unable to approve your advisor application at this time."
                        + (reason.isBlank() ? "" : " Reason: " + reason));
        emailService.sendAdvisorRejectionNotification(event);
    }

    /**
     * Persists the notification, if the event says who it is for.
     *
     * <p>Never throws. A malformed or unaddressable event must not become a poison pill: the
     * container would redeliver it forever and starve every well-formed event behind it on the
     * partition. Anything unusable is logged and dropped, and the email attempt still happens.
     */
    private void record(Map<String, Object> event, String type, String title, String body) {
        Optional<UUID> recipient = recipientId(event);
        if (recipient.isEmpty()) {
            log.warn("No recipient id on {} event — persisting no notification: {}", type, event);
            return;
        }
        try {
            notificationService.create(recipient.get(), type, title, body);
        } catch (RuntimeException e) {
            log.error("Failed to persist {} notification for userId={}: {}",
                    type, recipient.get(), e.getMessage(), e);
        }
    }

    /**
     * Who the notification belongs to.
     *
     * <p>{@code userId} on booking events; {@code advisorId} on {@code advisor.approved}, where
     * advisor-service keys the profile by the auth-service user id, so the two are the same
     * value. {@code advisor.rejected} carries neither — only an {@code applicationId}, which is
     * not a user id — so rejections currently produce an email but no inbox row. Closing that
     * gap needs advisor-service to put the applicant's user id on the event; guessing here
     * would file the notification under an id nobody can query.
     */
    private static Optional<UUID> recipientId(Map<String, Object> event) {
        for (String key : new String[]{"userId", "advisorId"}) {
            String raw = string(event, key);
            if (!raw.isBlank()) {
                try {
                    return Optional.of(UUID.fromString(raw.trim()));
                } catch (IllegalArgumentException ignored) {
                    // Not a UUID — fall through and try the next key.
                }
            }
        }
        return Optional.empty();
    }

    private static String string(Map<String, Object> event, String key) {
        Object raw = event == null ? null : event.get(key);
        return raw == null ? "" : raw.toString();
    }
}
