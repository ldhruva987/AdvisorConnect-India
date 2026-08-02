package com.advisorconnect.advisor.adapter.out.persistence;

import com.advisorconnect.advisor.domain.model.UserEmailCache;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface SpringDataUserEmailCacheRepository extends JpaRepository<UserEmailCache, UUID> {
}
