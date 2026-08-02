package com.advisorconnect.advisor.domain.model;

import com.advisorconnect.advisor.infrastructure.persistence.AesGcmStringConverter;
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
 *  - Private PII fields (_enc suffix) are encrypted in the application layer by
 *    {@link AesGcmStringConverter} before they ever reach the driver. An earlier version of
 *    this comment claimed pgcrypto handled it at the DB level; nothing did, and these columns
 *    held clear text.
 *  - Document metadata (S3 keys included) is never exposed in public API responses.
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

    // ── Private identity fields (AES-256-GCM, encrypted before leaving the JVM)
    //
    // These columns hold base64 ciphertext, not text. They are therefore not searchable,
    // sortable or joinable — which is fine, because nothing legitimately queries on a person's
    // date of birth. Widen the column definitions rather than the plaintext if they ever
    // overflow: GCM output is IV(12) + plaintext + tag(16), base64-expanded by 4/3.
    @Convert(converter = AesGcmStringConverter.class)
    @Column(name = "legal_first_name_enc")
    private String legalFirstName;

    @Convert(converter = AesGcmStringConverter.class)
    @Column(name = "legal_last_name_enc")
    private String legalLastName;

    @Convert(converter = AesGcmStringConverter.class)
    @Column(name = "date_of_birth_enc")
    private String dateOfBirth;

    @Convert(converter = AesGcmStringConverter.class)
    @Column(name = "address_enc")
    private String addressFull;

    @Convert(converter = AesGcmStringConverter.class)
    @Column(name = "country_enc")
    private String country;

    // ── Document references (never exposed via public API)
    @ElementCollection
    @CollectionTable(name = "application_documents")
    private List<DocumentMetadata> documents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationStatus status = ApplicationStatus.PENDING;

    private String adminNotes;
    private Instant submittedAt = Instant.now();
    private Instant reviewedAt;
    private UUID reviewedBy;
}
