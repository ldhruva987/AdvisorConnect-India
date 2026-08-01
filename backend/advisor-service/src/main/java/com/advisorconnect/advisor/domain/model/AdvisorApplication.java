package com.advisorconnect.advisor.domain.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Onboarding application — contains both public profile data and
 * references to private identity documents.
 *
 * Privacy architecture:
 *  - Public fields (username, bio, sectors) are visible to admins during review
 *    and promoted to AdvisorProfile on approval.
 *  - Private PII fields (_enc suffix) are stored encrypted via pgcrypto at DB level.
 *  - Document S3 keys are never exposed in public API responses.
 */
@Entity
@Table(name = "advisor_applications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdvisorApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID userId;

    // ── Public fields (shown in admin review, promoted to profile after approval)
    @Column(nullable = false)
    private String username;
    private String professionalTitle;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @ElementCollection
    @CollectionTable(name = "application_sectors")
    @Enumerated(EnumType.STRING)
    private List<AdvisorSector> sectors;

    private String qualification;
    private String fieldOfStudy;
    private String experienceYears;

    @Column(columnDefinition = "TEXT")
    private String previousWork;

    // ── Private identity fields (encrypted at rest via pgcrypto in DB)
    @Column(name = "legal_first_name_enc")
    private String legalFirstName;

    @Column(name = "legal_last_name_enc")
    private String legalLastName;

    @Column(name = "date_of_birth_enc")
    private String dateOfBirth;

    @Column(name = "address_enc")
    private String addressFull;

    @Column(name = "country_enc")
    private String country;

    // ── Document references (S3 object keys — never exposed via public API)
    @ElementCollection
    @CollectionTable(name = "application_documents")
    private List<String> documentS3Keys;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationStatus status = ApplicationStatus.PENDING;

    private String adminNotes;
    private Instant submittedAt = Instant.now();
    private Instant reviewedAt;
    private UUID reviewedBy;
}
