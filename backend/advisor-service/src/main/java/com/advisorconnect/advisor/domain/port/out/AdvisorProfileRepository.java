package com.advisorconnect.advisor.domain.port.out;

import com.advisorconnect.advisor.domain.model.AdvisorProfile;
import com.advisorconnect.advisor.domain.model.AdvisorSector;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface AdvisorProfileRepository {
    AdvisorProfile save(AdvisorProfile profile);
    Optional<AdvisorProfile> findByUsername(String username);
    Optional<AdvisorProfile> findById(UUID id);
    Page<AdvisorProfile> findBySector(AdvisorSector sector, Pageable pageable);
    Page<AdvisorProfile> findAll(Pageable pageable);
    Page<AdvisorProfile> search(String query, Pageable pageable);
}
