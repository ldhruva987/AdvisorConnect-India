package com.advisorconnect.advisor.adapter.out.persistence;

import com.advisorconnect.advisor.domain.model.AdvisorProfile;
import com.advisorconnect.advisor.domain.model.AdvisorSector;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface SpringDataAdvisorProfileRepository extends JpaRepository<AdvisorProfile, UUID> {
    Optional<AdvisorProfile> findByUsername(String username);

    @Query("SELECT DISTINCT p FROM AdvisorProfile p JOIN p.sectors s WHERE s = :sector")
    Page<AdvisorProfile> findBySectorsContaining(@Param("sector") AdvisorSector sector, Pageable pageable);

    @Query("SELECT p FROM AdvisorProfile p WHERE LOWER(p.username) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(p.professionalTitle) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(p.bio) LIKE LOWER(CONCAT('%', :q, '%'))")
    Page<AdvisorProfile> searchByQuery(@Param("q") String query, Pageable pageable);
}
