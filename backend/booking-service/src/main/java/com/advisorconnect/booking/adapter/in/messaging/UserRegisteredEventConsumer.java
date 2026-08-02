package com.advisorconnect.booking.adapter.in.messaging;

import com.advisorconnect.booking.domain.model.UserEmailCache;
import com.advisorconnect.booking.domain.port.out.UserEmailCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Maintains the local {@code userId -> email} projection from auth-service's
 * {@code user.registered} events.
 *
 * <p>Payload shape is auth-service's {@code AuthEventPublisher}: a flat map of
 * {@code eventType}, {@code userId}, {@code email}. Read defensively — this is a cross-service
 * contract, and a schema drift on the producer side should degrade to a logged warning here,
 * not a poison-pill loop.
 *
 * <p>Kafka is at-least-once and this consumer reads from {@code earliest}, so both redelivery
 * and full-topic replay have to be no-ops. They are: the row is keyed by the externally
 * assigned user id, so a repeat write is an idempotent overwrite.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserRegisteredEventConsumer {

    public static final String TOPIC_USER_REGISTERED = "user.registered";

    private final UserEmailCacheRepository userEmailCacheRepository;

    @KafkaListener(topics = TOPIC_USER_REGISTERED, groupId = "booking-service")
    @Transactional
    public void onUserRegistered(Map<String, Object> event) {
        Optional<UUID> userId = readUuid(event, "userId");
        String email = readString(event, "email");

        if (userId.isEmpty() || email == null || email.isBlank()) {
            // Redelivering a structurally broken event only replays the same failure, so it is
            // logged and acknowledged rather than retried forever.
            log.error("Ignoring malformed user.registered event: {}", event);
            return;
        }

        userEmailCacheRepository.save(UserEmailCache.builder()
                .id(userId.get())
                .email(email.trim())
                .build());
        log.info("Cached email for userId={}", userId.get());
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
