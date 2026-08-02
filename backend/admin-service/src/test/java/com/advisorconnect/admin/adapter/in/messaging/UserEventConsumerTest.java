package com.advisorconnect.admin.adapter.in.messaging;

import com.advisorconnect.admin.domain.port.out.PlatformCountersRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class UserEventConsumerTest {

    @Mock
    private PlatformCountersRepository counters;

    @InjectMocks
    private UserEventConsumer consumer;

    @Test
    @DisplayName("a user.registered event adds one user")
    void countsARegistration() {
        consumer.onUserRegistered(userRegistered(UUID.randomUUID(), "ada@example.com"));

        then(counters).should().addTotalUsers(1);
        then(counters).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("three registrations add three users")
    void countsEachRegistrationSeparately() {
        for (int i = 0; i < 3; i++) {
            consumer.onUserRegistered(userRegistered(UUID.randomUUID(), "user" + i + "@example.com"));
        }

        then(counters).should(times(3)).addTotalUsers(1);
    }

    @Test
    @DisplayName("a malformed payload still counts the registration and never throws")
    void toleratesAMalformedPayload() {
        // The event's arrival is the fact being counted; the fields are only used for logging.
        // Throwing here would stall the partition and freeze the whole dashboard.
        consumer.onUserRegistered(new HashMap<>());

        then(counters).should().addTotalUsers(1);
        then(counters).should(never()).addApprovedAdvisors(org.mockito.ArgumentMatchers.anyLong());
    }

    private static Map<String, Object> userRegistered(UUID userId, String email) {
        // Mirrors AuthEventPublisher.publishUserRegistered.
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "USER_REGISTERED");
        event.put("userId", userId.toString());
        event.put("email", email);
        return event;
    }
}
