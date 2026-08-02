package com.advisorconnect.advisor.adapter.in.messaging;

import com.advisorconnect.advisor.domain.model.UserEmailCache;
import com.advisorconnect.advisor.domain.port.out.UserEmailCacheRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Feeds the {@code user_email_cache} projection that lets approval and rejection events carry the
 * advisor's email. Payload shape is auth-service's {@code AuthEventPublisher}:
 * {@code {eventType, userId, email}}.
 */
@ExtendWith(MockitoExtension.class)
class UserRegisteredEventConsumerTest {

    @Mock
    private UserEmailCacheRepository repository;

    @InjectMocks
    private UserRegisteredEventConsumer consumer;

    @Test
    @DisplayName("a well-formed event caches the userId to email mapping")
    void cachesEmail() {
        UUID userId = UUID.randomUUID();

        consumer.onUserRegistered(event(userId.toString(), "ada@example.com"));

        UserEmailCache saved = captureSaved();
        assertThat(saved.getId()).isEqualTo(userId);
        assertThat(saved.getEmail()).isEqualTo("ada@example.com");
    }

    /**
     * Kafka is at-least-once and this consumer reads from {@code earliest}, so redelivery and
     * full-topic replay both happen routinely. The row is keyed by the externally assigned user
     * id, which makes the repeat write an overwrite rather than a duplicate-key failure.
     */
    @Test
    @DisplayName("redelivery of the same event is an idempotent overwrite, not a failure")
    void redeliveryIsIdempotent() {
        UUID userId = UUID.randomUUID();
        Map<String, Object> event = event(userId.toString(), "ada@example.com");

        assertThatCode(() -> {
            consumer.onUserRegistered(event);
            consumer.onUserRegistered(event);
            consumer.onUserRegistered(event);
        }).doesNotThrowAnyException();

        ArgumentCaptor<UserEmailCache> captor = ArgumentCaptor.forClass(UserEmailCache.class);
        verify(repository, times(3)).save(captor.capture());

        // Every write targets the same primary key with the same value, so the end state after
        // three deliveries is identical to the end state after one.
        List<UserEmailCache> writes = captor.getAllValues();
        assertThat(writes).allSatisfy(w -> {
            assertThat(w.getId()).isEqualTo(userId);
            assertThat(w.getEmail()).isEqualTo("ada@example.com");
        });
    }

    @Test
    @DisplayName("a later event for the same user replaces the cached address")
    void laterEventWins() {
        UUID userId = UUID.randomUUID();

        consumer.onUserRegistered(event(userId.toString(), "old@example.com"));
        consumer.onUserRegistered(event(userId.toString(), "new@example.com"));

        ArgumentCaptor<UserEmailCache> captor = ArgumentCaptor.forClass(UserEmailCache.class);
        verify(repository, times(2)).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("new@example.com");
    }

    @Test
    @DisplayName("surrounding whitespace is trimmed before the address is cached")
    void trimsEmail() {
        UUID userId = UUID.randomUUID();

        consumer.onUserRegistered(event(userId.toString(), "  ada@example.com  "));

        assertThat(captureSaved().getEmail()).isEqualTo("ada@example.com");
    }

    // ────────────────────────────────────────────────────────── malformed input

    /**
     * A structurally broken event cannot be fixed by retrying it, and this listener has no
     * dead-letter topic behind it — throwing would wedge the partition and stop every later
     * registration from being cached. Log and move on.
     */
    @ParameterizedTest
    @ValueSource(strings = {"not-a-uuid", "", "   "})
    @DisplayName("a malformed userId is dropped rather than poisoning the partition")
    void malformedUserIdIsDropped(String userId) {
        assertThatCode(() -> consumer.onUserRegistered(event(userId, "ada@example.com")))
                .doesNotThrowAnyException();

        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("a missing userId key is dropped")
    void missingUserIdIsDropped() {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "USER_REGISTERED");
        event.put("email", "ada@example.com");

        consumer.onUserRegistered(event);

        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("a blank email is dropped — a cache entry pointing nowhere is worse than none")
    void blankEmailIsDropped(String email) {
        consumer.onUserRegistered(event(UUID.randomUUID().toString(), email));

        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("a null email is dropped")
    void nullEmailIsDropped() {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "USER_REGISTERED");
        event.put("userId", UUID.randomUUID().toString());
        event.put("email", null);

        consumer.onUserRegistered(event);

        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("an entirely empty payload is dropped without throwing")
    void emptyPayloadIsDropped() {
        assertThatCode(() -> consumer.onUserRegistered(new HashMap<>()))
                .doesNotThrowAnyException();

        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("a null payload is dropped without throwing")
    void nullPayloadIsDropped() {
        assertThatCode(() -> consumer.onUserRegistered(null)).doesNotThrowAnyException();

        verifyNoInteractions(repository);
    }

    // ────────────────────────────────────────────────────────────────── helpers

    private static Map<String, Object> event(String userId, String email) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "USER_REGISTERED");
        event.put("userId", userId);
        event.put("email", email);
        return event;
    }

    private UserEmailCache captureSaved() {
        ArgumentCaptor<UserEmailCache> captor = ArgumentCaptor.forClass(UserEmailCache.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }
}
