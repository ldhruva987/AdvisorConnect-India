package com.advisorconnect.advisor.adapter.in.web.dto;

import com.advisorconnect.advisor.domain.model.AdvisorSector;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

@Data
public class SubmitApplicationRequest {

    @NotBlank
    @Pattern(regexp = "^[a-zA-Z0-9_]{3,30}$", message = "Username: letters, numbers, underscores only")
    private String username;

    @NotBlank
    private String professionalTitle;

    @NotBlank
    @Size(min = 50, max = 2000)
    private String bio;

    @NotEmpty
    private List<AdvisorSector> sectors;

    @NotBlank
    private String qualification;

    @NotBlank
    private String fieldOfStudy;

    @NotBlank
    private String experienceYears;

    private String previousWork;

    // Private identity — stored encrypted in DB, never returned in public DTOs
    @NotBlank
    private String legalFirstName;

    @NotBlank
    private String legalLastName;

    @NotBlank
    private String dateOfBirth;

    @NotBlank
    private String addressFull;

    @NotBlank
    private String country;

    // Document S3 keys — uploaded separately via pre-signed URL endpoint
    @NotEmpty
    private List<String> documentS3Keys;
}
