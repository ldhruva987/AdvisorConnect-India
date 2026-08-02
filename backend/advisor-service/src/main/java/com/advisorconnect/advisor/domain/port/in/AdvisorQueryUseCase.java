package com.advisorconnect.advisor.domain.port.in;

import com.advisorconnect.advisor.adapter.in.web.dto.AdvisorApplicationStatusDto;
import com.advisorconnect.advisor.adapter.in.web.dto.AdvisorApplicationSummaryDto;
import com.advisorconnect.advisor.adapter.in.web.dto.AdvisorPublicDto;
import com.advisorconnect.advisor.domain.model.AdvisorSector;
import com.advisorconnect.advisor.domain.model.ApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface AdvisorQueryUseCase {
    Page<AdvisorPublicDto> findAdvisors(AdvisorSector sector, String searchQuery, Pageable pageable);
    AdvisorPublicDto getAdvisorProfile(String username);
    AdvisorApplicationStatusDto getApplicationStatus(UUID userId);

    /**
     * Admin review queue. A null {@code status} returns every application regardless of state.
     * Results are PII-free summaries, never the entity.
     */
    Page<AdvisorApplicationSummaryDto> getApplications(ApplicationStatus status, Pageable pageable);
}
