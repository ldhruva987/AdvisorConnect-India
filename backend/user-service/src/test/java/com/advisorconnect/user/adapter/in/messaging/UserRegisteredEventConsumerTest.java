package com.advisorconnect.user.adapter.in.messaging;

import com.advisorconnect.user.application.UserProfileService;
import com.advisorconnect.user.domain.model.UserProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * The consumer that finally provisions {@code user_profiles} rows. Nothing did before, which is
 * why {@code GET /users/me} returned 404 for every real user on the platform.
 */
@ExtendWith(MockitoExtension.class)
class UserRegisteredEventConsumerTest {

    @Mock
    private UserProfileService userProfileService;

    @InjectMocks
    private UserRegisteredEventConsumer consumer;

    @Test
    @DisplayName("creates a profile with a username derived from the email local part")
    void createsProfile() {
        UUID userId = UUID.randomUUID();
        given(userProfileService.getById(userId)).willReturn(Optional.empty());

        consumer.onUserRegistered(registeredEvent(userId, "Alice.Smith@example.com"));

        verify(userProfileService).createFromRegistration(
                userId, "Alice.Smith@example.com", "alicesmith");
    }

    /**
     * Kafka is at-least-once and this consumer reads from {@code earliest}, so both redelivery and
     * a full-topic replay land here. A second delivery for the same user id must change nothing —
     * in particular it must not overwrite a username the user has since edited via
     * {@code PUT /users/me}.
     */
    @Test
    @DisplayName("a second delivery for the same userId is a safe no-op")
    void secondDeliveryIsNoOp() {
        UUID userId = UUID.randomUUID();
        Map<String, Object> event = registeredEvent(userId, "alice@example.com");

        given(userProfileService.getById(userId))
                .willReturn(Optional.empty())
                .willReturn(Optional.of(new UserProfile()));

        consumer.onUserRegistered(event);
        consumer.onUserRegistered(event);

        verify(userProfileService, times(1))
                .createFromRegistration(eq(userId), anyString(), anyString());
    }

    @Test
    @DisplayName("skips creation entirely when a profile already exists")
    void skipsWhenProfileExists() {
        UUID userId = UUID.randomUUID();
        given(userProfileService.getById(userId)).willReturn(Optional.of(new UserProfile()));

        consumer.onUserRegistered(registeredEvent(userId, "alice@example.com"));

        verify(userProfileService, never()).createFromRegistration(any(), anyString(), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "not-a-uuid"})
    @DisplayName("ignores a malformed userId rather than throwing into an infinite retry")
    void ignoresMalformedUserId(String userId) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "USER_REGISTERED");
        event.put("userId", userId);
        event.put("email", "alice@example.com");

        consumer.onUserRegistered(event);

        verify(userProfileService, never()).createFromRegistration(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("ignores an event with no email — a profile needs one, and the column is NOT NULL")
    void ignoresMissingEmail() {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "USER_REGISTERED");
        event.put("userId", UUID.randomUUID().toString());

        consumer.onUserRegistered(event);

        verify(userProfileService, never()).createFromRegistration(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("ignores a blank email")
    void ignoresBlankEmail() {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "USER_REGISTERED");
        event.put("userId", UUID.randomUUID().toString());
        event.put("email", "   ");

        consumer.onUserRegistered(event);

        verify(userProfileService, never()).createFromRegistration(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("ignores an entirely empty event")
    void ignoresEmptyEvent() {
        consumer.onUserRegistered(new HashMap<>());

        verify(userProfileService, never()).createFromRegistration(any(), anyString(), anyString());
    }

    /** Shaped exactly like auth-service's {@code AuthEventPublisher#publishUserRegistered}. */
    private static Map<String, Object> registeredEvent(UUID userId, String email) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "USER_REGISTERED");
        event.put("userId", userId.toString());
        event.put("email", email);
        return event;
    }
}
