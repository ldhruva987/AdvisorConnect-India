package com.advisorconnect.advisor.adapter.out.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdvisorEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishApplicationSubmitted(UUID applicationId, UUID userId, String username) {
        var payload = Map.of(
                "eventType", "ADVISOR_APPLICATION_SUBMITTED",
                "applicationId", applicationId.toString(),
                "userId", userId.toString(),
                "username", username
        );
        kafkaTemplate.send("advisor.application.submitted", applicationId.toString(), payload);
        log.info("Published ADVISOR_APPLICATION_SUBMITTED for applicationId={}", applicationId);
    }

    /**
     * @param reviewedBy    the administrator who approved the application. Carried so consumers of
     *                      a role change — auth-service promotes {@code USER} to {@code ADVISOR}
     *                      off this event — can attribute the privilege escalation to a human.
     *                      Without it, the audit trail for "who made this person an advisor" stops
     *                      at "some Kafka message".
     * @param advisorEmail  where notification-service sends the decision. advisor-service does not
     *                      own the email; it comes from the local {@code user_email_cache}
     *                      projection of auth-service's {@code user.registered} stream. Empty when
     *                      the cache has not seen the user yet — a gap in the projection must not
     *                      block an approval, so the event ships without it and the notification
     *                      is what degrades.
     */
    public void publishAdvisorApproved(UUID advisorId, String username, UUID reviewedBy, String advisorEmail) {
        var payload = Map.of(
                "eventType", "ADVISOR_APPROVED",
                "advisorId", advisorId.toString(),
                "username", username,
                // Map.of rejects nulls outright; the admin id is always present in practice
                // (it comes from the authenticated request), so this only guards the pathological
                // case rather than letting it blow up the whole approval transaction.
                "reviewedBy", reviewedBy != null ? reviewedBy.toString() : "",
                // Key is "email" to match the name auth-service uses on user.registered — this is
                // the same field travelling further downstream, and renaming it mid-flight would
                // only cost consumers a lookup table.
                "email", advisorEmail != null ? advisorEmail : ""
        );
        kafkaTemplate.send("advisor.approved", advisorId.toString(), payload);
        log.info("Published ADVISOR_APPROVED for advisorId={} reviewedBy={} emailPresent={}",
                advisorId, reviewedBy, advisorEmail != null && !advisorEmail.isBlank());
    }

    /**
     * @param reviewedBy   the administrator who rejected the application — see above.
     * @param advisorEmail the applicant's email, from the same cache — see above.
     */
    public void publishAdvisorRejected(UUID applicationId, String reason, UUID reviewedBy, String advisorEmail) {
        var payload = Map.of(
                "eventType", "ADVISOR_REJECTED",
                "applicationId", applicationId.toString(),
                "reason", reason != null ? reason : "",
                "reviewedBy", reviewedBy != null ? reviewedBy.toString() : "",
                "email", advisorEmail != null ? advisorEmail : ""
        );
        kafkaTemplate.send("advisor.rejected", applicationId.toString(), payload);
        log.info("Published ADVISOR_REJECTED for applicationId={} reviewedBy={} emailPresent={}",
                applicationId, reviewedBy, advisorEmail != null && !advisorEmail.isBlank());
    }
}
