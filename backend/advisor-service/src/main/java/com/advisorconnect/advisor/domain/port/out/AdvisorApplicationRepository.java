package com.advisorconnect.advisor.domain.port.out;

import com.advisorconnect.advisor.domain.model.AdvisorApplication;
import com.advisorconnect.advisor.domain.model.ApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface AdvisorApplicationRepository {
    AdvisorApplication save(AdvisorApplication application);
    Optional<AdvisorApplication> findById(UUID id);
    Optional<AdvisorApplication> findByUserId(UUID userId);
    boolean existsByUserId(UUID userId);

    /** Backs the admin review queue, which is filtered by status in the common case. */
    Page<AdvisorApplication> findByStatus(ApplicationStatus status, Pageable pageable);

    /** Unfiltered variant, for when an admin wants the whole history rather than one bucket. */
    Page<AdvisorApplication> findAll(Pageable pageable);
}
