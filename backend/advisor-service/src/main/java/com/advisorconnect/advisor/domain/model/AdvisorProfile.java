package com.advisorconnect.advisor.domain.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Public-facing advisor profile — safe to return to any caller.
 * Contains NO PII; identity documents are held in AdvisorApplication.
 */
@Entity
@Table(name = "advisor_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdvisorProfile {

    @Id
    private UUID id; // same as userId from Auth Service

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String professionalTitle;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @ElementCollection
    @CollectionTable(name = "advisor_tags")
    private List<String> tags;

    @ElementCollection
    @CollectionTable(name = "advisor_sectors")
    @Enumerated(EnumType.STRING)
    private List<AdvisorSector> sectors;

    @ElementCollection
    @CollectionTable(name = "advisor_languages")
    private List<String> languages;

    private BigDecimal averageRating = BigDecimal.ZERO;
    private int reviewCount = 0;
    private int chatCount = 0;
    private int responseTimeMinutes = 0;
    private int experienceYears;
    private boolean isOnline = false;
    private boolean isVerified = false;
    private String avatarColor;

    private Instant createdAt = Instant.now();
}
