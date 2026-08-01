package com.advisorconnect.advisor.adapter.in.web;

import com.advisorconnect.advisor.adapter.in.web.dto.*;
import com.advisorconnect.advisor.domain.model.AdvisorSector;
import com.advisorconnect.advisor.domain.port.in.AdvisorCommandUseCase;
import com.advisorconnect.advisor.domain.port.in.AdvisorQueryUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/advisors")
@RequiredArgsConstructor
public class AdvisorController {

    private final AdvisorQueryUseCase queryUseCase;
    private final AdvisorCommandUseCase commandUseCase;

    /** Public — list advisors with optional sector/text filtering */
    @GetMapping
    public Page<AdvisorPublicDto> listAdvisors(
            @RequestParam(required = false) AdvisorSector sector,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20) Pageable pageable) {
        return queryUseCase.findAdvisors(sector, q, pageable);
    }

    /** Public — get advisor public profile by username */
    @GetMapping("/{username}")
    public AdvisorPublicDto getProfile(@PathVariable String username) {
        return queryUseCase.getAdvisorProfile(username);
    }

    /** Authenticated — submit onboarding application */
    @PostMapping("/apply")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public UUID submitApplication(
            @Valid @RequestBody SubmitApplicationRequest request,
            @RequestHeader("X-User-Id") UUID userId) {
        return commandUseCase.submitApplication(request, userId);
    }

    /** Authenticated — check own application status (no PII returned) */
    @GetMapping("/applications/status")
    @PreAuthorize("isAuthenticated()")
    public AdvisorApplicationStatusDto getMyApplicationStatus(
            @RequestHeader("X-User-Id") UUID userId) {
        return queryUseCase.getApplicationStatus(userId);
    }

    /** Admin only — approve an advisor application */
    @PutMapping("/applications/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public void approve(
            @PathVariable UUID id,
            @RequestBody AdminDecisionRequest req,
            @RequestHeader("X-User-Id") UUID adminId) {
        commandUseCase.approveAdvisor(id, adminId, req.getNotes());
    }

    /** Admin only — reject an advisor application */
    @PutMapping("/applications/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public void reject(
            @PathVariable UUID id,
            @RequestBody AdminDecisionRequest req,
            @RequestHeader("X-User-Id") UUID adminId) {
        commandUseCase.rejectAdvisor(id, adminId, req.getNotes());
    }
}
