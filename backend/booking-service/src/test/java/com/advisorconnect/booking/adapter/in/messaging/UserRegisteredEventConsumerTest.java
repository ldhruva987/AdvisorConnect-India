package com.advisorconnect.booking.adapter.in.messaging;

import com.advisorconnect.booking.domain.model.UserEmailCache;
import com.advisorconnect.booking.domain.port.out.UserEmailCacheRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
 * The local {@code userId -> email} projection fed by auth-service's {@code user.registered}.
 *
 * <p>Two properties matter. The write has to be an idempotent upsert, because Kafka is
 * at-least-once and this consumer starts from {@code earliest} — a replay of the whole topic
 * must be a no-op rather than a wall of duplicate-key failures. And a malformed event must be
 * dropped, not retried, or it becomes a poison pill that starves every event behind it on the
 * partition.
 */
@ExtendWith(MockitoExtension.class)
class UserRegisteredEventConsumerTest {

    @Mock
    private UserEmailCacheRepository userEmailCacheRepository;

    @InjectMocks
    private UserRegisteredEventConsumer consumer;

    // ------------------------------------------------------------------------- the happy path

    @Test
    @DisplayName("a user.registered event caches the email under the auth-service user id")
    void cachesTheEmail() {
        UUID userId = UUID.randomUUID();

        consumer.onUserRegistered(event(userId, "someone@example.com"));

        UserEmailCache saved = captureSaved();
        assertThat(saved.getId()).isEqualTo(userId);
        assertThat(saved.getEmail()).isEqualTo("someone@example.com");
    }

    @Test
    @DisplayName("surrounding whitespace is trimmed off both the id and the email")
    void trimsWhitespace() {
        UUID userId = UUID.randomUUID();
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "USER_REGISTERED");
        event.put("userId", "  " + userId + "  ");
        event.put("email", "  spaced@example.com  ");

        consumer.onUserRegistered(event);

        UserEmailCache saved = captureSaved();
        assertThat(saved.getId()).isEqualTo(userId);
        assertThat(saved.getEmail()).isEqualTo("spaced@example.com");
    }

    // ------------------------------------------------------------------------ idempotency

    @Test
    @DisplayName("redelivery of the same event writes the same row again rather than failing")
    void redeliveryIsIdempotent() {
        UUID userId = UUID.randomUUID();
        Map<String, Object> event = event(userId, "dup@example.com");

        consumer.onUserRegistered(event);
        consumer.onUserRegistered(event);
        consumer.onUserRegistered(event);

        ArgumentCaptor<UserEmailCache> captor = ArgumentCaptor.forClass(UserEmailCache.class);
        verify(userEmailCacheRepository, times(3)).save(captor.capture());

        // Every write targets the same externally assigned primary key, which is what makes the
        // repeat a merge rather than a duplicate insert.
        assertThat(captor.getAllValues())
                .extracting(UserEmailCache::getId)
                .containsExactly(userId, userId, userId);
        assertThat(captor.getAllValues())
                .extracting(UserEmailCache::getEmail)
                .containsOnly("dup@example.com");
    }

    @Test
    @DisplayName("a later event for the same user overwrites the cached address")
    void laterEventWins() {
        UUID userId = UUID.randomUUID();

        consumer.onUserRegistered(event(userId, "old@example.com"));
        consumer.onUserRegistered(event(userId, "new@example.com"));

        ArgumentCaptor<UserEmailCache> captor = ArgumentCaptor.forClass(UserEmailCache.class);
        verify(userEmailCacheRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(1).getEmail()).isEqualTo("new@example.com");
    }

    // ------------------------------------------------------------------ malformed payloads

    @Test
    @DisplayName("a malformed event is dropped rather than retried forever")
    void malformedEventsAreDropped() {
        List<Map<String, Object>> broken = List.of(
                Map.of("eventType", "USER_REGISTERED"),                              // nothing
                Map.of("userId", UUID.randomUUID().toString()),                      // no email
                Map.of("userId", UUID.randomUUID().toString(), "email", "   "),      // blank
                Map.of("email", "orphan@example.com"),                               // no id
                Map.of("userId", "not-a-uuid", "email", "bad@example.com"));         // bad id

        for (Map<String, Object> event : broken) {
            assertThatCode(() -> consumer.onUserRegistered(event)).doesNotThrowAnyException();
        }

        verifyNoInteractions(userEmailCacheRepository);
    }

    @Test
    @DisplayName("a null payload does not blow up the listener")
    void nullPayloadIsTolerated() {
        assertThatCode(() -> consumer.onUserRegistered(null)).doesNotThrowAnyException();

        verifyNoInteractions(userEmailCacheRepository);
    }

    // ------------------------------------------------------------------------------ helpers

    private UserEmailCache captureSaved() {
        ArgumentCaptor<UserEmailCache> captor = ArgumentCaptor.forClass(UserEmailCache.class);
        verify(userEmailCacheRepository).save(captor.capture());
        return captor.getValue();
    }

    /** The exact payload shape auth-service's {@code AuthEventPublisher} sends. */
    private static Map<String, Object> event(UUID userId, String email) {
        return Map.of(
                "eventType", "USER_REGISTERED",
                "userId", userId.toString(),
                "email", email);
    }
}
