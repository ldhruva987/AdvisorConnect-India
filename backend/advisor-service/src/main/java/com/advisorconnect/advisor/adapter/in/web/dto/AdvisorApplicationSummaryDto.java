package com.advisorconnect.advisor.adapter.in.web.dto;

import com.advisorconnect.advisor.domain.model.AdvisorSector;
import com.advisorconnect.advisor.domain.model.ApplicationStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Row in the admin application queue.
 *
 * <p>Carries no PII by construction. Legal name, date of birth, address, country and document
 * S3 keys are all absent — the list view is a triage surface, and serialising the entity would
 * have shipped decrypted identity documents to every admin session that opened page one.
 * {@code documentCount} is the deliberate substitute: enough to see whether a submission is
 * complete, not enough to read anything.
 */
@Data
@Builder
public class AdvisorApplicationSummaryDto {

    private UUID id;
    private UUID userId;
    private String username;
    private String professionalTitle;
    private List<AdvisorSector> sectors;
    private String qualification;
    private String experienceYears;
    private ApplicationStatus status;
    private Instant submittedAt;
    private int documentCount;
}
