package com.advisorconnect.user.adapter.out.persistence;

import com.advisorconnect.user.domain.model.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Package-private on purpose: Spring Data is an implementation detail of
 * {@link JpaUserProfileRepository}, and nothing outside this package should bind to it.
 */
interface SpringDataUserProfileRepository extends JpaRepository<UserProfile, UUID> {
    boolean existsByUsername(String username);
}
