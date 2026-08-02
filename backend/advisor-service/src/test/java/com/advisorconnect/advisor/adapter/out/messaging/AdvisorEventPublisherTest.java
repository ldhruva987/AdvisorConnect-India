package com.advisorconnect.advisor.adapter.out.messaging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Approval and rejection are privilege decisions — auth-service promotes an account to
 * {@code ADVISOR} straight off {@code advisor.approved} — so the events must name the
 * administrator who made them. They previously did not, leaving no way to attribute the
 * escalation to a human.
 */
@ExtendWith(MockitoExtension.class)
class AdvisorEventPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private AdvisorEventPublisher publisher;

    @Test
    @DisplayName("advisor.approved carries advisorId, username and reviewedBy, keyed by advisorId")
    void approvedCarriesReviewer() {
        UUID advisorId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        publisher.publishAdvisorApproved(advisorId, "some-advisor", adminId, "advisor@example.com");

        Map<String, Object> payload = capturePayload("advisor.approved", advisorId.toString());
        assertThat(payload)
                .containsEntry("eventType", "ADVISOR_APPROVED")
                .containsEntry("advisorId", advisorId.toString())
                .containsEntry("username", "some-advisor")
                .containsEntry("reviewedBy", adminId.toString())
                .containsEntry("email", "advisor@example.com");
    }

    @Test
    @DisplayName("advisor.rejected carries applicationId, reason and reviewedBy")
    void rejectedCarriesReviewer() {
        UUID applicationId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        publisher.publishAdvisorRejected(
                applicationId, "insufficient evidence", adminId, "advisor@example.com");

        Map<String, Object> payload = capturePayload("advisor.rejected", applicationId.toString());
        assertThat(payload)
                .containsEntry("eventType", "ADVISOR_REJECTED")
                .containsEntry("applicationId", applicationId.toString())
                .containsEntry("reason", "insufficient evidence")
                .containsEntry("reviewedBy", adminId.toString())
                .containsEntry("email", "advisor@example.com");
    }

    /**
     * {@code Map.of} throws on a null value, which would abort the whole approval transaction
     * over a missing audit field. The publisher substitutes an empty string instead.
     */
    @Test
    @DisplayName("a null reviewer degrades to an empty string rather than killing the approval")
    void nullReviewerDoesNotThrow() {
        UUID advisorId = UUID.randomUUID();

        assertThatCode(() -> publisher.publishAdvisorApproved(
                advisorId, "some-advisor", null, "advisor@example.com"))
                .doesNotThrowAnyException();

        assertThat(capturePayload("advisor.approved", advisorId.toString()))
                .containsEntry("reviewedBy", "");
    }

    @Test
    @DisplayName("a null rejection reason degrades to an empty string")
    void nullReasonDoesNotThrow() {
        UUID applicationId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        assertThatCode(() -> publisher.publishAdvisorRejected(
                applicationId, null, adminId, "advisor@example.com"))
                .doesNotThrowAnyException();

        assertThat(capturePayload("advisor.rejected", applicationId.toString()))
                .containsEntry("reason", "");
    }

    /**
     * The email is what notification-service needs in order to tell the applicant anything at
     * all. It was absent from both events, which is why approval mail was never sent.
     */
    @Test
    @DisplayName("advisor.approved carries the advisor's email under the key auth-service uses")
    void approvedCarriesEmail() {
        UUID advisorId = UUID.randomUUID();

        publisher.publishAdvisorApproved(advisorId, "some-advisor", UUID.randomUUID(), "ada@example.com");

        assertThat(capturePayload("advisor.approved", advisorId.toString()))
                .containsEntry("email", "ada@example.com");
    }

    /**
     * The cache is an eventually-consistent projection of another service's topic, so it can
     * legitimately not have the address yet. That must not abort an approval an admin has
     * already made — the event ships with an empty email instead.
     */
    @Test
    @DisplayName("a null email degrades to an empty string rather than killing the approval")
    void nullEmailDoesNotThrow() {
        UUID advisorId = UUID.randomUUID();

        assertThatCode(() -> publisher.publishAdvisorApproved(
                advisorId, "some-advisor", UUID.randomUUID(), null))
                .doesNotThrowAnyException();

        assertThat(capturePayload("advisor.approved", advisorId.toString()))
                .containsEntry("email", "");
    }

    @Test
    @DisplayName("a null email on rejection likewise degrades to an empty string")
    void nullEmailOnRejectionDoesNotThrow() {
        UUID applicationId = UUID.randomUUID();

        assertThatCode(() -> publisher.publishAdvisorRejected(
                applicationId, "no", UUID.randomUUID(), null))
                .doesNotThrowAnyException();

        assertThat(capturePayload("advisor.rejected", applicationId.toString()))
                .containsEntry("email", "");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> capturePayload(String topic, String key) {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq(topic), eq(key), captor.capture());
        return (Map<String, Object>) captor.getValue();
    }
}
