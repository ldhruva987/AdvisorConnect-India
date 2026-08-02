package com.advisorconnect.booking.adapter.out.persistence;

import com.advisorconnect.booking.domain.model.UserEmailCache;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface SpringDataUserEmailCacheRepository extends JpaRepository<UserEmailCache, UUID> {
}
