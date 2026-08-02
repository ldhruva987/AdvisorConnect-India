package com.advisorconnect.user.domain.port.out;

import com.advisorconnect.user.domain.model.UserProfile;

import java.util.Optional;
import java.util.UUID;

/**
 * Output port — persistence abstraction for {@link UserProfile}.
 *
 * <p>Introduced so the application layer stops reaching for a raw {@code EntityManager}: user-service
 * previously had no repository at all and {@code UserController} called {@code em.find(...)}
 * directly, which put JPA in the web layer and left every read and write outside a transaction.
 * Mirrors the port/adapter split advisor-service already uses.
 */
public interface UserProfileRepository {

    UserProfile save(UserProfile profile);

    Optional<UserProfile> findById(UUID id);

    boolean existsById(UUID id);

    /** Backs the collision suffixing in auto-generated usernames. */
    boolean existsByUsername(String username);
}
