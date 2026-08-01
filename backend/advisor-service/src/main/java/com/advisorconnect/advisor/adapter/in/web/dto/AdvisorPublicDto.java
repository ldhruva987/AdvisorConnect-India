package com.advisorconnect.advisor.adapter.in.web.dto;

import com.advisorconnect.advisor.domain.model.AdvisorSector;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * DTO returned to public callers — contains NO private/PII data.
 * Identity documents and legal name fields are never included here.
 */
@Data
@Builder
public class AdvisorPublicDto {
    private UUID id;
    private String username;
    private String professionalTitle;
    private String bio;
    private List<String> tags;
    private List<AdvisorSector> sectors;
    private List<String> languages;
    private BigDecimal averageRating;
    private int reviewCount;
    private int chatCount;
    private int responseTimeMinutes;
    private int experienceYears;
    private boolean isOnline;
    private boolean isVerified;
    private String avatarColor;
}
