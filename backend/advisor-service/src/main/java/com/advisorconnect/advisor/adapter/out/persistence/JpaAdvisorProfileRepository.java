package com.advisorconnect.advisor.adapter.out.persistence;

import com.advisorconnect.advisor.domain.model.AdvisorProfile;
import com.advisorconnect.advisor.domain.model.AdvisorSector;
import com.advisorconnect.advisor.domain.port.out.AdvisorProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaAdvisorProfileRepository implements AdvisorProfileRepository {

    private final SpringDataAdvisorProfileRepository repo;

    @Override public AdvisorProfile save(AdvisorProfile profile) { return repo.save(profile); }
    @Override public Optional<AdvisorProfile> findByUsername(String username) { return repo.findByUsername(username); }
    @Override public Optional<AdvisorProfile> findById(UUID id) { return repo.findById(id); }
    @Override public Page<AdvisorProfile> findBySector(AdvisorSector sector, Pageable pageable) { return repo.findBySectorsContaining(sector, pageable); }
    @Override public Page<AdvisorProfile> findAll(Pageable pageable) { return repo.findAll(pageable); }
    @Override public Page<AdvisorProfile> search(String query, Pageable pageable) { return repo.searchByQuery(query, pageable); }
}
