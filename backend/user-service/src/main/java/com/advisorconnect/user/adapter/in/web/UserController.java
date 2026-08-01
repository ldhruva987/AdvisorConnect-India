package com.advisorconnect.user.adapter.in.web;

import com.advisorconnect.user.domain.model.UserProfile;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/users")
public class UserController {

    @PersistenceContext
    private EntityManager em;

    /** GET /users/{id} — returns public user profile */
    @GetMapping("/{id}")
    public ResponseEntity<UserProfile> getProfile(@PathVariable UUID id) {
        UserProfile profile = em.find(UserProfile.class, id);
        if (profile == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(profile);
    }

    /** GET /users/me — current authenticated user profile (resolved via gateway header) */
    @GetMapping("/me")
    public ResponseEntity<UserProfile> getMyProfile(
            @RequestHeader("X-User-Id") UUID userId) {
        UserProfile profile = em.find(UserProfile.class, userId);
        if (profile == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(profile);
    }

    /** PUT /users/me — update own profile */
    @PutMapping("/me")
    public ResponseEntity<UserProfile> updateProfile(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestBody UpdateProfileRequest req) {
        UserProfile profile = em.find(UserProfile.class, userId);
        if (profile == null) return ResponseEntity.notFound().build();
        if (req.displayName() != null) profile.setDisplayName(req.displayName());
        if (req.bio() != null) profile.setBio(req.bio());
        return ResponseEntity.ok(profile);
    }

    record UpdateProfileRequest(String displayName, String bio) {}
}
