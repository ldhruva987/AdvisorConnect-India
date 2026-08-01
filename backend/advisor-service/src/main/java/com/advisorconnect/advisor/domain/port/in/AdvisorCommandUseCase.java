package com.advisorconnect.advisor.domain.port.in;

import com.advisorconnect.advisor.adapter.in.web.dto.SubmitApplicationRequest;

import java.util.UUID;

public interface AdvisorCommandUseCase {
    UUID submitApplication(SubmitApplicationRequest request, UUID userId);
    void approveAdvisor(UUID applicationId, UUID adminId, String notes);
    void rejectAdvisor(UUID applicationId, UUID adminId, String reason);
    void requestMoreInfo(UUID applicationId, UUID adminId, String message);
}
