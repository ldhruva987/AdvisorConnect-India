package com.advisorconnect.admin.adapter.in.messaging;

import com.advisorconnect.admin.application.AuditLogService;
import com.advisorconnect.admin.domain.model.AuditLog;
import com.advisorconnect.admin.domain.port.out.PlatformCountersRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AdvisorEventConsumerTest {

    @Mock
    private PlatformCountersRepository counters;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private AdvisorEventConsumer consumer;

    // ----------------------------------------------------------------- application submitted

    @Test
    @DisplayName("a submitted application joins the pending queue and is audited")
    void submittedApplicationIncrementsPendingAndAudits() {
        UUID applicationId = UUID.randomUUID();

        consumer.onApplicationSubmitted(submitted(applicationId, UUID.randomUUID(), "ada"));

        then(counters).should().addPendingApplications(1);
        then(counters).shouldHaveNoMoreInteractions();
        then(auditLogService).should().record(
                eq(AuditLog.SYSTEM_ACTOR),
                eq("ADVISOR_APPLICATION_SUBMITTED"),
                eq(applicationId.toString()),
                eq("AdvisorApplication"),
                anyString());
    }

    @Test
    @DisplayName("a submission is attributed to the system actor, not to the applicant")
    void submissionIsNotAttributedToAnAdministrator() {
        UUID applicantId = UUID.randomUUID();

        consumer.onApplicationSubmitted(submitted(UUID.randomUUID(), applicantId, "ada"));

        // Recording the applicant as adminId would make the audit trail claim the applicant
        // performed an administrative action on themselves.
        then(auditLogService).should(never()).record(
                eq(applicantId), anyString(), any(), any(), any());
    }

    // ---------------------------------------------------------------------------- approved

    @Test
    @DisplayName("an approval moves one application out of pending and into approved advisors")
    void approvalMovesTheApplicationOutOfPending() {
        UUID advisorId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        consumer.onAdvisorApproved(approved(advisorId, "ada", adminId.toString()));

        InOrder order = inOrder(counters);
        order.verify(counters).addPendingApplications(-1);
        order.verify(counters).addApprovedAdvisors(1);
        then(counters).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("the approval is audited against the reviewing administrator")
    void approvalIsAttributedToTheReviewer() {
        UUID advisorId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        consumer.onAdvisorApproved(approved(advisorId, "ada", adminId.toString()));

        then(auditLogService).should().record(
                eq(adminId),
                eq("ADVISOR_APPROVED"),
                eq(advisorId.toString()),
                eq("Advisor"),
                anyString());
    }

    @Test
    @DisplayName("an approval with a blank reviewedBy still records, attributed to the system")
    void approvalWithoutAReviewerFallsBackToTheSystemActor() {
        // AdvisorEventPublisher builds its payload with Map.of, which rejects nulls, so it sends
        // an empty string when it has no admin id. Dropping the event over that would lose the
        // approval from the counters entirely.
        UUID advisorId = UUID.randomUUID();

        consumer.onAdvisorApproved(approved(advisorId, "ada", ""));

        then(counters).should().addApprovedAdvisors(1);
        then(auditLogService).should().record(
                eq(AuditLog.SYSTEM_ACTOR), eq("ADVISOR_APPROVED"), eq(advisorId.toString()),
                anyString(), anyString());
    }

    @Test
    @DisplayName("a reviewedBy that is not a UUID degrades instead of stalling the partition")
    void approvalWithAGarbageReviewerStillCounts() {
        consumer.onAdvisorApproved(approved(UUID.randomUUID(), "ada", "not-a-uuid"));

        then(counters).should().addApprovedAdvisors(1);
        then(auditLogService).should().record(
                eq(AuditLog.SYSTEM_ACTOR), anyString(), any(), any(), any());
    }

    // ---------------------------------------------------------------------------- rejected

    @Test
    @DisplayName("a rejection clears the pending slot without creating an advisor")
    void rejectionDecrementsPendingOnly() {
        UUID applicationId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        consumer.onAdvisorRejected(rejected(applicationId, "Insufficient experience", adminId.toString()));

        then(counters).should().addPendingApplications(-1);
        then(counters).should(never()).addApprovedAdvisors(anyLong());
        then(counters).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("the rejection reason and reviewer are both preserved in the audit trail")
    void rejectionAuditCarriesTheReasonAndReviewer() {
        UUID applicationId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        consumer.onAdvisorRejected(rejected(applicationId, "Insufficient experience", adminId.toString()));

        then(auditLogService).should().record(
                eq(adminId),
                eq("ADVISOR_REJECTED"),
                eq(applicationId.toString()),
                eq("AdvisorApplication"),
                eq("Rejected: Insufficient experience"));
    }

    @Test
    @DisplayName("a rejection with no reason still audits")
    void rejectionWithoutAReasonStillAudits() {
        UUID applicationId = UUID.randomUUID();

        consumer.onAdvisorRejected(rejected(applicationId, "", UUID.randomUUID().toString()));

        then(auditLogService).should().record(
                any(), eq("ADVISOR_REJECTED"), eq(applicationId.toString()), anyString(),
                eq("Rejected: no reason given"));
    }

    // ------------------------------------------------------------------------------ funnel

    @Test
    @DisplayName("submit-then-approve leaves the pending queue where it started")
    void theFunnelBalancesOut() {
        UUID applicationId = UUID.randomUUID();
        UUID advisorId = UUID.randomUUID();

        consumer.onApplicationSubmitted(submitted(applicationId, UUID.randomUUID(), "ada"));
        consumer.onAdvisorApproved(approved(advisorId, "ada", UUID.randomUUID().toString()));

        then(counters).should().addPendingApplications(1);
        then(counters).should().addPendingApplications(-1);
        then(counters).should().addApprovedAdvisors(1);
    }

    // ----------------------------------------------------------------------------- payloads
    // Field names mirror advisor-service's AdvisorEventPublisher exactly.

    private static Map<String, Object> submitted(UUID applicationId, UUID userId, String username) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "ADVISOR_APPLICATION_SUBMITTED");
        event.put("applicationId", applicationId.toString());
        event.put("userId", userId.toString());
        event.put("username", username);
        return event;
    }

    private static Map<String, Object> approved(UUID advisorId, String username, String reviewedBy) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "ADVISOR_APPROVED");
        event.put("advisorId", advisorId.toString());
        event.put("username", username);
        event.put("reviewedBy", reviewedBy);
        return event;
    }

    private static Map<String, Object> rejected(UUID applicationId, String reason, String reviewedBy) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "ADVISOR_REJECTED");
        event.put("applicationId", applicationId.toString());
        event.put("reason", reason);
        event.put("reviewedBy", reviewedBy);
        return event;
    }
}
