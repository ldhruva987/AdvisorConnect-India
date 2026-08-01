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

    public void publishAdvisorApproved(UUID advisorId, String username) {
        var payload = Map.of(
                "eventType", "ADVISOR_APPROVED",
                "advisorId", advisorId.toString(),
                "username", username
        );
        kafkaTemplate.send("advisor.approved", advisorId.toString(), payload);
        log.info("Published ADVISOR_APPROVED for advisorId={}", advisorId);
    }

    public void publishAdvisorRejected(UUID applicationId, String reason) {
        var payload = Map.of(
                "eventType", "ADVISOR_REJECTED",
                "applicationId", applicationId.toString(),
                "reason", reason != null ? reason : ""
        );
        kafkaTemplate.send("advisor.rejected", applicationId.toString(), payload);
        log.info("Published ADVISOR_REJECTED for applicationId={}", applicationId);
    }
}
