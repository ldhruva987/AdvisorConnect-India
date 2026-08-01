package com.advisorconnect.notification.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailNotificationService {

    private final JavaMailSender mailSender;
    private static final String FROM = "noreply@advisorconnect.com";

    public void sendBookingConfirmation(Map<String, Object> event) {
        String to = (String) event.getOrDefault("userEmail", "");
        if (to.isBlank()) {
            log.warn("No email address provided for booking confirmation event");
            return;
        }
        send(to,
                "Your AdvisorConnect session is confirmed!",
                "Your video session has been booked. Check your calendar for details.\n\nAdvisorConnect Team");
    }

    public void sendBookingCancellation(Map<String, Object> event) {
        String to = (String) event.getOrDefault("userEmail", "");
        if (to.isBlank()) return;
        send(to,
                "Your session has been cancelled",
                "Your booking has been cancelled and a refund has been initiated within 5-10 business days.\n\nAdvisorConnect Team");
    }

    public void sendAdvisorApprovalNotification(Map<String, Object> event) {
        String to = (String) event.getOrDefault("advisorEmail", "");
        if (to.isBlank()) return;
        send(to,
                "Your AdvisorConnect application is approved!",
                "Congratulations! Your advisor application has been approved. You are now live on the platform.\n\nAdvisorConnect Team");
    }

    public void sendAdvisorRejectionNotification(Map<String, Object> event) {
        String to = (String) event.getOrDefault("advisorEmail", "");
        if (to.isBlank()) return;
        String reason = (String) event.getOrDefault("reason", "Please contact support for more information.");
        send(to,
                "Update on your AdvisorConnect application",
                "Thank you for applying. After reviewing your application, we are unable to approve it at this time.\n\n"
                        + "Reason: " + reason + "\n\nAdvisorConnect Team");
    }

    private void send(String to, String subject, String body) {
        try {
            var msg = new SimpleMailMessage();
            msg.setFrom(FROM);
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            mailSender.send(msg);
            log.info("Email sent to={} subject='{}'", to, subject);
        } catch (Exception e) {
            log.error("Failed to send email to={}: {}", to, e.getMessage());
        }
    }
}
