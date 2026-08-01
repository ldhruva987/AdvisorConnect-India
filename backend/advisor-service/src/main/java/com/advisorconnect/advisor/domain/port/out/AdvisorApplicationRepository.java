package com.advisorconnect.advisor.domain.port.out;

import com.advisorconnect.advisor.domain.model.AdvisorApplication;

import java.util.Optional;
import java.util.UUID;

public interface AdvisorApplicationRepository {
    AdvisorApplication save(AdvisorApplication application);
    Optional<AdvisorApplication> findById(UUID id);
    Optional<AdvisorApplication> findByUserId(UUID userId);
    boolean existsByUserId(UUID userId);
}
