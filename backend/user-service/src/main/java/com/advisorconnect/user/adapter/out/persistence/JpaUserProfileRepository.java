package com.advisorconnect.user.adapter.out.persistence;

import com.advisorconnect.user.domain.model.UserProfile;
import com.advisorconnect.user.domain.port.out.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaUserProfileRepository implements UserProfileRepository {

    private final SpringDataUserProfileRepository repo;

    @Override public UserProfile save(UserProfile profile) { return repo.save(profile); }
    @Override public Optional<UserProfile> findById(UUID id) { return repo.findById(id); }
    @Override public boolean existsById(UUID id) { return repo.existsById(id); }
    @Override public boolean existsByUsername(String username) { return repo.existsByUsername(username); }
}
