package com.advisorconnect.admin.adapter.in.messaging;

import com.advisorconnect.admin.domain.port.out.PlatformCountersRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Keeps the {@code totalUsers} figure current from auth-service's {@code user.registered}.
 *
 * <p>Payload: {@code eventType}, {@code userId}, {@code email} — see {@code AuthEventPublisher}.
 * Only the fact of the event matters here; no audit entry is written, because a self-service
 * signup is not an administrator action.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventConsumer {

    private final PlatformCountersRepository counters;

    @KafkaListener(topics = "user.registered", groupId = "admin-service")
    @Transactional
    public void onUserRegistered(Map<String, Object> event) {
        counters.addTotalUsers(1);
        log.info("user.registered consumed, totalUsers += 1 (userId={})",
                EventPayloads.stringOr(event, "userId", "unknown"));
    }
}
