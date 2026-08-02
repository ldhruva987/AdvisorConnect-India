package com.advisorconnect.advisor.adapter.out.persistence;

import com.advisorconnect.advisor.domain.model.AdvisorApplication;
import com.advisorconnect.advisor.domain.model.ApplicationStatus;
import com.advisorconnect.advisor.domain.port.out.AdvisorApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaAdvisorApplicationRepository implements AdvisorApplicationRepository {

    private final SpringDataAdvisorApplicationRepository repo;

    @Override public AdvisorApplication save(AdvisorApplication application) { return repo.save(application); }
    @Override public Optional<AdvisorApplication> findById(UUID id) { return repo.findById(id); }
    @Override public Optional<AdvisorApplication> findByUserId(UUID userId) { return repo.findByUserId(userId); }
    @Override public boolean existsByUserId(UUID userId) { return repo.existsByUserId(userId); }

    @Override
    public Page<AdvisorApplication> findByStatus(ApplicationStatus status, Pageable pageable) {
        return repo.findByStatus(status, pageable);
    }

    @Override
    public Page<AdvisorApplication> findAll(Pageable pageable) {
        return repo.findAll(pageable);
    }
}
