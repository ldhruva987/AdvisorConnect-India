package com.advisorconnect.auth.adapter.in.messaging;

import com.advisorconnect.auth.domain.model.User;
import com.advisorconnect.auth.domain.model.UserRole;
import com.advisorconnect.auth.domain.port.out.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Promotes an account to {@code ADVISOR} once advisor-service approves its application.
 *
 * <p>Nothing performed this promotion before, which made {@link UserRole#ADVISOR} unreachable in
 * practice: an approved advisor kept a {@code USER} token, so every {@code hasRole('ADVISOR')}
 * check in the platform failed for exactly the people it was written for.
 *
 * <p>The advisor's id in the event <em>is</em> the auth-service user id — advisor-service keys
 * both {@code AdvisorApplication.userId} and {@code AdvisorProfile.id} to it.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdvisorApprovedEventConsumer {

    private final UserRepository userRepository;

    @KafkaListener(topics = "advisor.approved", groupId = "auth-service")
    @Transactional
    public void onAdvisorApproved(Map<String, Object> event) {
        Optional<UUID> advisorId = readUuid(event, "advisorId");
        if (advisorId.isEmpty()) {
            // Nothing to retry towards — redelivering a malformed event just replays the same
            // failure forever, so it is logged and acknowledged.
            log.error("Ignoring advisor.approved event with missing/malformed advisorId: {}", event);
            return;
        }

        Optional<User> found = userRepository.findById(advisorId.get());
        if (found.isEmpty()) {
            log.error("Ignoring advisor.approved for unknown userId={}", advisorId.get());
            return;
        }

        User user = found.get();

        if (user.getRole() == UserRole.ADMIN) {
            // An administrator who also advises keeps the stronger role; overwriting it here
            // would be a silent privilege *downgrade* triggered by an unrelated workflow.
            log.warn("Not demoting ADMIN userId={} to ADVISOR on advisor.approved", user.getId());
            return;
        }

        if (user.getRole() == UserRole.ADVISOR) {
            // Kafka is at-least-once; a redelivery must be a no-op, not a second write.
            log.info("userId={} is already an ADVISOR — advisor.approved ignored as duplicate",
                    user.getId());
            return;
        }

        user.setRole(UserRole.ADVISOR);
        userRepository.save(user);
        log.info("Promoted userId={} from USER to ADVISOR", user.getId());
    }

    private static Optional<UUID> readUuid(Map<String, Object> event, String key) {
        Object raw = event == null ? null : event.get(key);
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(raw.toString().trim()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
