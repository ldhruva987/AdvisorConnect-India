package com.advisorconnect.advisor.adapter.in.web.dto;

import com.advisorconnect.advisor.domain.model.ApplicationStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class AdvisorApplicationStatusDto {
    private UUID applicationId;
    private ApplicationStatus status;
    private Instant submittedAt;
    private String adminMessage;
}
