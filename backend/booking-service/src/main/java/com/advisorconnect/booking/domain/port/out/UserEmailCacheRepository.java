package com.advisorconnect.booking.domain.port.out;

import com.advisorconnect.booking.domain.model.UserEmailCache;

import java.util.Optional;
import java.util.UUID;

public interface UserEmailCacheRepository {

    /**
     * Inserts or replaces the cached email for a user. The id is externally assigned, so this is
     * an upsert — which is what makes {@code user.registered} redelivery a no-op rather than a
     * duplicate-key failure.
     */
    UserEmailCache save(UserEmailCache entry);

    Optional<UserEmailCache> findById(UUID userId);
}
