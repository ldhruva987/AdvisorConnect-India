package com.advisorconnect.advisor.application;

import com.advisorconnect.advisor.adapter.in.web.dto.*;
import com.advisorconnect.advisor.adapter.out.messaging.AdvisorEventPublisher;
import com.advisorconnect.advisor.domain.model.*;
import com.advisorconnect.advisor.domain.port.in.AdvisorCommandUseCase;
import com.advisorconnect.advisor.domain.port.in.AdvisorQueryUseCase;
import com.advisorconnect.advisor.domain.port.out.AdvisorApplicationRepository;
import com.advisorconnect.advisor.domain.port.out.AdvisorProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class AdvisorApplicationService implements AdvisorQueryUseCase, AdvisorCommandUseCase {

    private final AdvisorProfileRepository profileRepository;
    private final AdvisorApplicationRepository applicationRepository;
    private final AdvisorEventPublisher eventPublisher;

    // ── Query use case ──────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<AdvisorPublicDto> findAdvisors(AdvisorSector sector, String searchQuery, Pageable pageable) {
        Page<AdvisorProfile> profiles;
        if (searchQuery != null && !searchQuery.isBlank()) {
            profiles = profileRepository.search(searchQuery, pageable);
        } else if (sector != null) {
            profiles = profileRepository.findBySector(sector, pageable);
        } else {
            profiles = profileRepository.findAll(pageable);
        }
        return profiles.map(this::toPublicDto);
    }

    @Override
    @Transactional(readOnly = true)
    public AdvisorPublicDto getAdvisorProfile(String username) {
        AdvisorProfile profile = profileRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Advisor not found: " + username));
        return toPublicDto(profile);
    }

    @Override
    @Transactional(readOnly = true)
    public AdvisorApplicationStatusDto getApplicationStatus(UUID userId) {
        AdvisorApplication app = applicationRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("No application found for user"));
        return AdvisorApplicationStatusDto.builder()
                .applicationId(app.getId())
                .status(app.getStatus())
                .submittedAt(app.getSubmittedAt())
                .adminMessage(app.getAdminNotes())
                .build();
    }

    // ── Command use case ────────────────────────────────────────────────────────

    @Override
    public UUID submitApplication(SubmitApplicationRequest req, UUID userId) {
        if (applicationRepository.existsByUserId(userId)) {
            throw new IllegalStateException("Application already submitted");
        }
        AdvisorApplication app = AdvisorApplication.builder()
                .userId(userId)
                .username(req.getUsername())
                .professionalTitle(req.getProfessionalTitle())
                .bio(req.getBio())
                .sectors(req.getSectors())
                .qualification(req.getQualification())
                .fieldOfStudy(req.getFieldOfStudy())
                .experienceYears(req.getExperienceYears())
                .previousWork(req.getPreviousWork())
                .legalFirstName(req.getLegalFirstName())
                .legalLastName(req.getLegalLastName())
                .dateOfBirth(req.getDateOfBirth())
                .addressFull(req.getAddressFull())
                .country(req.getCountry())
                .documentS3Keys(req.getDocumentS3Keys())
                .status(ApplicationStatus.PENDING)
                .build();
        app = applicationRepository.save(app);
        eventPublisher.publishApplicationSubmitted(app.getId(), userId, req.getUsername());
        return app.getId();
    }

    @Override
    public void approveAdvisor(UUID applicationId, UUID adminId, String notes) {
        AdvisorApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found"));
        app.setStatus(ApplicationStatus.APPROVED);
        app.setAdminNotes(notes);
        app.setReviewedAt(Instant.now());
        app.setReviewedBy(adminId);
        applicationRepository.save(app);

        // Promote to public profile
        AdvisorProfile profile = AdvisorProfile.builder()
                .id(app.getUserId())
                .username(app.getUsername())
                .professionalTitle(app.getProfessionalTitle())
                .bio(app.getBio())
                .sectors(app.getSectors())
                .averageRating(BigDecimal.ZERO)
                .isVerified(true)
                .build();
        profileRepository.save(profile);
        eventPublisher.publishAdvisorApproved(app.getUserId(), app.getUsername());
    }

    @Override
    public void rejectAdvisor(UUID applicationId, UUID adminId, String reason) {
        AdvisorApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found"));
        app.setStatus(ApplicationStatus.REJECTED);
        app.setAdminNotes(reason);
        app.setReviewedAt(Instant.now());
        app.setReviewedBy(adminId);
        applicationRepository.save(app);
        eventPublisher.publishAdvisorRejected(applicationId, reason);
    }

    @Override
    public void requestMoreInfo(UUID applicationId, UUID adminId, String message) {
        AdvisorApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found"));
        app.setStatus(ApplicationStatus.NEEDS_MORE_INFO);
        app.setAdminNotes(message);
        app.setReviewedBy(adminId);
        applicationRepository.save(app);
    }

    // ── Mapper ──────────────────────────────────────────────────────────────────

    private AdvisorPublicDto toPublicDto(AdvisorProfile p) {
        return AdvisorPublicDto.builder()
                .id(p.getId())
                .username(p.getUsername())
                .professionalTitle(p.getProfessionalTitle())
                .bio(p.getBio())
                .tags(p.getTags())
                .sectors(p.getSectors())
                .languages(p.getLanguages())
                .averageRating(p.getAverageRating())
                .reviewCount(p.getReviewCount())
                .chatCount(p.getChatCount())
                .responseTimeMinutes(p.getResponseTimeMinutes())
                .experienceYears(p.getExperienceYears())
                .isOnline(p.isOnline())
                .isVerified(p.isVerified())
                .avatarColor(p.getAvatarColor())
                .build();
    }
}
