package com.advisorconnect.notification.adapter.in.messaging;

import com.advisorconnect.notification.application.EmailNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingEventConsumer {

    private final EmailNotificationService emailService;

    @KafkaListener(topics = "booking.created", groupId = "notification-service")
    public void onBookingCreated(Map<String, Object> event) {
        log.info("Received booking.created event: {}", event);
        emailService.sendBookingConfirmation(event);
    }

    @KafkaListener(topics = "booking.cancelled", groupId = "notification-service")
    public void onBookingCancelled(Map<String, Object> event) {
        log.info("Received booking.cancelled event: {}", event);
        emailService.sendBookingCancellation(event);
    }

    @KafkaListener(topics = "advisor.approved", groupId = "notification-service")
    public void onAdvisorApproved(Map<String, Object> event) {
        log.info("Received advisor.approved event: {}", event);
        emailService.sendAdvisorApprovalNotification(event);
    }

    @KafkaListener(topics = "advisor.rejected", groupId = "notification-service")
    public void onAdvisorRejected(Map<String, Object> event) {
        log.info("Received advisor.rejected event: {}", event);
        emailService.sendAdvisorRejectionNotification(event);
    }
}
