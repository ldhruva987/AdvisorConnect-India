package com.advisorconnect.auth.adapter.out.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Outbound Kafka adapter for auth-service, mirroring advisor-service's
 * {@code AdvisorEventPublisher}: flat {@code Map} payloads with an {@code eventType}
 * discriminator, keyed by the aggregate id so all events for one user land on one partition
 * and are therefore consumed in order.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuthEventPublisher {

    /** Topic name; also referenced by user-service's {@code UserRegisteredEventConsumer}. */
    public static final String TOPIC_USER_REGISTERED = "user.registered";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Announces a newly registered account so downstream services can provision their own
     * projection of it — most importantly user-service, which creates the {@code UserProfile}
     * row that {@code GET /users/me} reads.
     *
     * <p>No password material or role is carried: the role at registration is always
     * {@code USER}, and any later change is announced by its own event.
     */
    public void publishUserRegistered(UUID userId, String email) {
        var payload = Map.of(
                "eventType", "USER_REGISTERED",
                "userId", userId.toString(),
                "email", email
        );
        kafkaTemplate.send(TOPIC_USER_REGISTERED, userId.toString(), payload);
        log.info("Published USER_REGISTERED for userId={}", userId);
    }
}
