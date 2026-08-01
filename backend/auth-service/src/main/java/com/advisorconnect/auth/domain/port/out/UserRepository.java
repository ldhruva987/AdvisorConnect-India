package com.advisorconnect.auth.domain.port.out;

import com.advisorconnect.auth.domain.model.User;
import java.util.Optional;
import java.util.UUID;

/**
 * Output port — persistence abstraction.
 * The domain layer depends on this interface; the infrastructure adapter implements it.
 */
public interface UserRepository {
    User save(User user);
    Optional<User> findByEmail(String email);
    Optional<User> findById(UUID id);
    boolean existsByEmail(String email);
}
