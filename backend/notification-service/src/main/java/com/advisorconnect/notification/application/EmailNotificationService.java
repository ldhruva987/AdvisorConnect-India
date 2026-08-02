package com.advisorconnect.notification.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
public class EmailNotificationService {

    private final JavaMailSender mailSender;

    /**
     * The envelope sender.
     *
     * <p>Was a hardcoded {@code noreply@advisorconnect.com} constant, while every deployment
     * configures {@code MAIL_FROM=noreply@advisorconnect.io} — docker-compose and
     * {@code application-docker.yml} both set the {@code .io} address, and nothing read it. So
     * the address the platform is configured to send from was dead config, and the one it
     * actually used was a domain the platform does not operate — exactly the mismatch that gets
     * mail rejected by SPF/DKIM.
     *
     * <p>Injected rather than constant so there is one source of truth per environment.
     */
    private final String from;

    /**
     * Explicit constructor rather than Lombok's {@code @RequiredArgsConstructor}: Lombok does
     * not copy {@code @Value} onto the generated constructor parameter unless configured to,
     * so the annotation would be silently dropped and the field left null.
     */
    public EmailNotificationService(JavaMailSender mailSender,
                                    @Value("${mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    public void sendBookingConfirmation(Map<String, Object> event) {
        String to = recipient(event, "userEmail");
        if (to.isBlank()) {
            log.warn("No email address provided for booking confirmation event");
            return;
        }
        send(to,
                "Your AdvisorConnect session is confirmed!",
                "Your video session has been booked. Check your calendar for details.\n\nAdvisorConnect Team");
    }

    public void sendBookingCancellation(Map<String, Object> event) {
        String to = recipient(event, "userEmail");
        if (to.isBlank()) return;
        send(to,
                "Your session has been cancelled",
                "Your booking has been cancelled and a refund has been initiated within 5-10 business days.\n\nAdvisorConnect Team");
    }

    public void sendAdvisorApprovalNotification(Map<String, Object> event) {
        String to = recipient(event, "advisorEmail");
        if (to.isBlank()) return;
        send(to,
                "Your AdvisorConnect application is approved!",
                "Congratulations! Your advisor application has been approved. You are now live on the platform.\n\nAdvisorConnect Team");
    }

    public void sendAdvisorRejectionNotification(Map<String, Object> event) {
        String to = recipient(event, "advisorEmail");
        if (to.isBlank()) return;
        String reason = String.valueOf(
                event.getOrDefault("reason", "Please contact support for more information."));
        send(to,
                "Update on your AdvisorConnect application",
                "Thank you for applying. After reviewing your application, we are unable to approve it at this time.\n\n"
                        + "Reason: " + reason + "\n\nAdvisorConnect Team");
    }

    /**
     * The recipient address, read from {@code preferredKey} and falling back to {@code email}.
     *
     * <p>The producers disagree on the key: booking-service sends {@code userEmail}, while
     * advisor-service's {@code AdvisorEventPublisher} sends the advisor's address under plain
     * {@code email} (matching the name auth-service uses on {@code user.registered}) rather
     * than the {@code advisorEmail} this service assumed. Accepting both keys fixes advisor
     * mail without touching another service's wire format; without it, approval and rejection
     * mail silently goes nowhere for exactly the reason booking mail did.
     *
     * <p>Blank when absent, which every caller treats as "do not send".
     */
    private static String recipient(Map<String, Object> event, String preferredKey) {
        if (event == null) return "";
        Object value = event.get(preferredKey);
        if (value == null || value.toString().isBlank()) {
            value = event.get("email");
        }
        return value == null ? "" : value.toString().trim();
    }

    private void send(String to, String subject, String body) {
        try {
            var msg = new SimpleMailMessage();
            msg.setFrom(from);
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            mailSender.send(msg);
            log.info("Email sent from={} to={} subject='{}'", from, to, subject);
        } catch (Exception e) {
            log.error("Failed to send email to={}: {}", to, e.getMessage());
        }
    }
}
