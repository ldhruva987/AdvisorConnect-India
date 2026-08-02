package com.advisorconnect.booking.adapter.out.persistence;

import com.advisorconnect.booking.domain.model.UserEmailCache;
import com.advisorconnect.booking.domain.port.out.UserEmailCacheRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaUserEmailCacheRepository implements UserEmailCacheRepository {

    private final SpringDataUserEmailCacheRepository repo;

    /**
     * {@code JpaRepository.save} on an entity with an assigned id issues a merge, so a repeated
     * {@code user.registered} for the same user overwrites the row instead of colliding on the
     * primary key.
     */
    @Override
    public UserEmailCache save(UserEmailCache entry) {
        return repo.save(entry);
    }

    @Override
    public Optional<UserEmailCache> findById(UUID userId) {
        return repo.findById(userId);
    }
}
