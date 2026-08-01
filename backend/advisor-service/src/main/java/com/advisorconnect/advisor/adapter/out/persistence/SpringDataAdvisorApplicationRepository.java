package com.advisorconnect.advisor.adapter.out.persistence;

import com.advisorconnect.advisor.domain.model.AdvisorApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface SpringDataAdvisorApplicationRepository extends JpaRepository<AdvisorApplication, UUID> {
    Optional<AdvisorApplication> findByUserId(UUID userId);
    boolean existsByUserId(UUID userId);
}
