package com.advisorconnect.user.adapter.in.messaging;

import com.advisorconnect.user.application.UserProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Creates the {@code UserProfile} row for a newly registered account.
 *
 * <p>This is the missing link that made {@code GET /users/me} return 404 for every real user:
 * auth-service owned the credentials, user-service owned the profile, and nothing connected the
 * two, so the profile table stayed permanently empty in production.
 *
 * <p>Consumption is idempotent — see {@link UserProfileService#createFromRegistration}. Kafka is
 * at-least-once, and this consumer reads from {@code earliest}, so both redelivery and full-topic
 * replay must be no-ops rather than duplicate inserts.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserRegisteredEventConsumer {

    private final UserProfileService userProfileService;

    @KafkaListener(topics = "user.registered", groupId = "user-service")
    public void onUserRegistered(Map<String, Object> event) {
        Optional<UUID> userId = readUuid(event, "userId");
        String email = readString(event, "email");

        if (userId.isEmpty() || email == null || email.isBlank()) {
            // Redelivering a structurally broken event only replays the same failure, so it is
            // logged and acknowledged rather than retried forever.
            log.error("Ignoring malformed user.registered event: {}", event);
            return;
        }

        if (userProfileService.getById(userId.get()).isPresent()) {
            log.info("Profile already exists for userId={} — user.registered ignored as duplicate",
                    userId.get());
            return;
        }

        userProfileService.createFromRegistration(
                userId.get(), email, UserProfileService.usernameFromEmail(email));
    }

    private static Optional<UUID> readUuid(Map<String, Object> event, String key) {
        String raw = readString(event, key);
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(raw.trim()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static String readString(Map<String, Object> event, String key) {
        Object raw = event == null ? null : event.get(key);
        return raw == null ? null : raw.toString();
    }
}
