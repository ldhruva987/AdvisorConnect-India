package com.advisorconnect.admin.adapter.in.messaging;

import com.advisorconnect.admin.application.AuditLogService;
import com.advisorconnect.admin.domain.model.AuditLog;
import com.advisorconnect.admin.domain.port.out.PlatformCountersRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Tracks the advisor application funnel and records each decision in the audit trail.
 *
 * <p>Payload shapes, from advisor-service's {@code AdvisorEventPublisher}:
 * <ul>
 *   <li>{@code advisor.application.submitted}: {@code applicationId}, {@code userId},
 *       {@code username}</li>
 *   <li>{@code advisor.approved}: {@code advisorId}, {@code username}, {@code reviewedBy},
 *       {@code email}</li>
 *   <li>{@code advisor.rejected}: {@code applicationId}, {@code reason}, {@code reviewedBy},
 *       {@code email}</li>
 * </ul>
 *
 * <p>{@code email} is the applicant's address, carried for notification-service. Nothing here
 * reads it: the dashboard has no use for it, and copying personal data into the audit trail
 * would put it somewhere with a much longer retention than the topic it arrived on.
 *
 * <p>Auditing here rather than trusting the admin UI to post an entry is the point: the audit
 * trail should record what the platform actually did, not what a client remembered to report.
 *
 * <p>Counter and audit row are written in one transaction, so the trail can never claim a
 * decision the counters do not reflect.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdvisorEventConsumer {

    private final PlatformCountersRepository counters;
    private final AuditLogService auditLogService;

    @KafkaListener(topics = "advisor.application.submitted", groupId = "admin-service")
    @Transactional
    public void onApplicationSubmitted(Map<String, Object> event) {
        String applicationId = EventPayloads.string(event, "applicationId");
        String username = EventPayloads.stringOr(event, "username", "unknown");

        counters.addPendingApplications(1);
        // No administrator is involved in a submission, so the actor is the platform itself.
        auditLogService.record(
                AuditLog.SYSTEM_ACTOR,
                "ADVISOR_APPLICATION_SUBMITTED",
                applicationId,
                "AdvisorApplication",
                "Application submitted by " + username);

        log.info("advisor.application.submitted consumed, pendingApplications += 1 (applicationId={})",
                applicationId);
    }

    @KafkaListener(topics = "advisor.approved", groupId = "admin-service")
    @Transactional
    public void onAdvisorApproved(Map<String, Object> event) {
        String advisorId = EventPayloads.string(event, "advisorId");
        String username = EventPayloads.stringOr(event, "username", "unknown");
        UUID reviewedBy = reviewer(event);

        counters.addPendingApplications(-1);
        counters.addApprovedAdvisors(1);
        auditLogService.record(
                reviewedBy,
                "ADVISOR_APPROVED",
                advisorId,
                "Advisor",
                "Approved advisor " + username);

        log.info("advisor.approved consumed, approvedAdvisors += 1 (advisorId={} reviewedBy={})",
                advisorId, reviewedBy);
    }

    @KafkaListener(topics = "advisor.rejected", groupId = "admin-service")
    @Transactional
    public void onAdvisorRejected(Map<String, Object> event) {
        String applicationId = EventPayloads.string(event, "applicationId");
        String reason = EventPayloads.stringOr(event, "reason", "no reason given");
        UUID reviewedBy = reviewer(event);

        counters.addPendingApplications(-1);
        auditLogService.record(
                reviewedBy,
                "ADVISOR_REJECTED",
                applicationId,
                "AdvisorApplication",
                "Rejected: " + reason);

        log.info("advisor.rejected consumed, pendingApplications -= 1 (applicationId={} reviewedBy={})",
                applicationId, reviewedBy);
    }

    /**
     * The reviewing administrator. {@code AdvisorEventPublisher} sends {@code ""} when it has no
     * admin id (it builds the payload with {@code Map.of}, which rejects nulls), so an unusable
     * value is expected rather than exceptional — it degrades to the system actor instead of
     * failing the event and blocking the partition.
     */
    private UUID reviewer(Map<String, Object> event) {
        return EventPayloads.uuid(event, "reviewedBy").orElseGet(() -> {
            log.warn("advisor decision event carried no usable reviewedBy; attributing to the system actor");
            return AuditLog.SYSTEM_ACTOR;
        });
    }
}
