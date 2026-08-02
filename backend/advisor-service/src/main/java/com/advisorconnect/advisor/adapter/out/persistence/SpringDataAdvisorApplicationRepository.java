package com.advisorconnect.advisor.adapter.out.persistence;

import com.advisorconnect.advisor.domain.model.AdvisorApplication;
import com.advisorconnect.advisor.domain.model.ApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface SpringDataAdvisorApplicationRepository extends JpaRepository<AdvisorApplication, UUID> {
    Optional<AdvisorApplication> findByUserId(UUID userId);
    boolean existsByUserId(UUID userId);
    Page<AdvisorApplication> findByStatus(ApplicationStatus status, Pageable pageable);
}
