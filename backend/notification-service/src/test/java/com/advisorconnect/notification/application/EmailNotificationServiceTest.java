package com.advisorconnect.notification.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * The first test in notification-service, which shipped with none.
 *
 * <p>Two bugs are pinned here. Booking events never carried {@code userEmail}, and this class
 * returns without sending when the address is blank — so the no-op path was the <em>only</em>
 * path ever taken in production, silently. And the From address was a hardcoded
 * {@code noreply@advisorconnect.com} while every deployment configures
 * {@code noreply@advisorconnect.io}, so the configured value was dead and the sent value came
 * from a domain the platform does not operate.
 */
@ExtendWith(MockitoExtension.class)
class EmailNotificationServiceTest {

    /**
     * Deliberately not the production default. If the service ever falls back to a constant
     * again, the From assertions below fail rather than coincidentally passing.
     */
    private static final String FROM = "sender-under-test@example.org";

    @Mock
    private JavaMailSender mailSender;

    private EmailNotificationService service;

    @BeforeEach
    void setUp() {
        service = new EmailNotificationService(mailSender, FROM);
    }

    // ------------------------------------------------------------------ the injected sender

    @Test
    @DisplayName("the From address is the injected mail.from value, not a hardcoded literal")
    void fromAddressComesFromConfiguration() {
        service.sendBookingConfirmation(event("userEmail", "client@example.com"));

        SimpleMailMessage sent = captureSent();
        assertThat(sent.getFrom()).isEqualTo(FROM);
        assertThat(sent.getFrom()).isNotEqualTo("noreply@advisorconnect.com");
    }

    @Test
    @DisplayName("a differently configured sender is used verbatim")
    void anotherConfiguredSenderIsHonoured() {
        var other = new EmailNotificationService(mailSender, "billing@advisorconnect.io");

        other.sendBookingCancellation(event("userEmail", "client@example.com"));

        assertThat(captureSent().getFrom()).isEqualTo("billing@advisorconnect.io");
    }

    // -------------------------------------------------------------------- booking confirmation

    @Nested
    @DisplayName("sendBookingConfirmation")
    class BookingConfirmation {

        @Test
        @DisplayName("sends to the address on the event")
        void sendsWhenTheAddressIsPresent() {
            service.sendBookingConfirmation(event("userEmail", "client@example.com"));

            SimpleMailMessage sent = captureSent();
            assertThat(sent.getTo()).containsExactly("client@example.com");
            assertThat(sent.getSubject()).contains("confirmed");
            assertThat(sent.getText()).isNotBlank();
        }

        @Test
        @DisplayName("no-ops when userEmail is missing — this was every booking, before the fix")
        void noOpsWhenTheKeyIsAbsent() {
            assertThatCode(() -> service.sendBookingConfirmation(
                    Map.of("eventType", "BOOKING_CREATED", "bookingId", "b-1")))
                    .doesNotThrowAnyException();

            verifyNoInteractions(mailSender);
        }

        @Test
        @DisplayName("no-ops when userEmail is blank or whitespace")
        void noOpsWhenBlank() {
            service.sendBookingConfirmation(event("userEmail", ""));
            service.sendBookingConfirmation(event("userEmail", "   "));

            verifyNoInteractions(mailSender);
        }
    }

    // -------------------------------------------------------------------------- other events

    @Test
    @DisplayName("cancellation mails the client and no-ops without an address")
    void cancellation() {
        service.sendBookingCancellation(event("userEmail", "client@example.com"));
        assertThat(captureSent().getTo()).containsExactly("client@example.com");
    }

    @Test
    @DisplayName("cancellation no-ops when the event carries no address")
    void cancellationNoOps() {
        service.sendBookingCancellation(Map.of("bookingId", "b-1"));
        verifyNoInteractions(mailSender);
    }

    @Test
    @DisplayName("advisor approval reads the address advisor-service actually sends, under 'email'")
    void approvalReadsTheProducersKey() {
        // AdvisorEventPublisher puts the address under "email", matching the name auth-service
        // uses on user.registered — not "advisorEmail", which is what this service assumed and
        // is why approval mail also went nowhere.
        service.sendAdvisorApprovalNotification(event("email", "advisor@example.com"));

        assertThat(captureSent().getTo()).containsExactly("advisor@example.com");
    }

    @Test
    @DisplayName("advisor approval still honours the legacy advisorEmail key")
    void approvalHonoursTheLegacyKey() {
        service.sendAdvisorApprovalNotification(event("advisorEmail", "advisor@example.com"));

        assertThat(captureSent().getTo()).containsExactly("advisor@example.com");
    }

    @Test
    @DisplayName("advisor rejection includes the reason from the event")
    void rejectionIncludesTheReason() {
        Map<String, Object> event = new HashMap<>();
        event.put("email", "advisor@example.com");
        event.put("reason", "Insufficient documentation");

        service.sendAdvisorRejectionNotification(event);

        assertThat(captureSent().getText()).contains("Insufficient documentation");
    }

    @Test
    @DisplayName("advisor rejection falls back to a generic reason when none is given")
    void rejectionWithoutAReason() {
        service.sendAdvisorRejectionNotification(event("email", "advisor@example.com"));

        assertThat(captureSent().getText()).contains("contact support");
    }

    @Test
    @DisplayName("approval and rejection no-op without an address")
    void advisorEventsNoOpWithoutAnAddress() {
        service.sendAdvisorApprovalNotification(Map.of("advisorId", "a-1"));
        service.sendAdvisorRejectionNotification(Map.of("applicationId", "app-1"));

        verifyNoInteractions(mailSender);
    }

    // ------------------------------------------------------------------------- delivery failure

    @Test
    @DisplayName("an SMTP failure is swallowed — a dead mail server must not kill the listener")
    void smtpFailureDoesNotPropagate() {
        willThrow(new MailSendException("connection refused"))
                .given(mailSender).send(any(SimpleMailMessage.class));

        assertThatCode(() -> service.sendBookingConfirmation(event("userEmail", "c@example.com")))
                .doesNotThrowAnyException();
    }

    // -------------------------------------------------------------------------------- helpers

    private SimpleMailMessage captureSent() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }

    private static Map<String, Object> event(String key, String value) {
        Map<String, Object> event = new HashMap<>();
        event.put(key, value);
        return event;
    }
}
