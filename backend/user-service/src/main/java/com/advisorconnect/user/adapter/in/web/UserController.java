package com.advisorconnect.user.adapter.in.web;

import com.advisorconnect.common.security.SecurityHeaders;
import com.advisorconnect.user.application.UserProfileService;
import com.advisorconnect.user.domain.model.UserProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Endpoint shapes are unchanged from the {@code EntityManager}-backed version — same paths, same
 * response bodies, same 404 when a profile is missing. What changed is underneath: persistence
 * goes through {@link UserProfileService} now, so the web layer no longer holds a
 * {@code @PersistenceContext} and every read and write happens inside a transaction. The 404 is
 * raised as {@code UserProfileNotFoundException} and mapped back to the same status by
 * {@link GlobalExceptionHandler}.
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserProfileService userProfileService;

    /** GET /users/{id} — returns public user profile */
    @GetMapping("/{id}")
    public UserProfile getProfile(@PathVariable UUID id) {
        return userProfileService.getOrThrow(id);
    }

    /** GET /users/me — current authenticated user profile (resolved via gateway header) */
    @GetMapping("/me")
    public UserProfile getMyProfile(@RequestHeader(SecurityHeaders.X_USER_ID) UUID userId) {
        return userProfileService.getOrThrow(userId);
    }

    /** PUT /users/me — update own profile */
    @PutMapping("/me")
    public UserProfile updateProfile(@RequestHeader(SecurityHeaders.X_USER_ID) UUID userId,
                                     @RequestBody UpdateProfileRequest req) {
        return userProfileService.updateProfile(userId, req.displayName(), req.bio());
    }

    record UpdateProfileRequest(String displayName, String bio) {}
}
