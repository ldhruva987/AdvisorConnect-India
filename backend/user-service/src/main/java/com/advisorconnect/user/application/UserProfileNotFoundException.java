package com.advisorconnect.user.application;

import java.util.UUID;

/**
 * Thrown when a profile is requested for a user id that has none. Mapped to {@code 404} by
 * {@code GlobalExceptionHandler}, preserving the status the controller returned back when it
 * null-checked {@code EntityManager.find} itself.
 */
public class UserProfileNotFoundException extends RuntimeException {

    public UserProfileNotFoundException(UUID userId) {
        super("No profile for user " + userId);
    }
}
