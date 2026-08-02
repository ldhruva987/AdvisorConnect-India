package com.advisorconnect.auth.adapter.in.messaging;

import com.advisorconnect.auth.domain.model.User;
import com.advisorconnect.auth.domain.model.UserRole;
import com.advisorconnect.auth.domain.port.out.UserRepository;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Nothing promoted an approved advisor's account before this consumer existed, so
 * {@link UserRole#ADVISOR} was unreachable in practice: an approved advisor kept a {@code USER}
 * token and failed every {@code hasRole('ADVISOR')} check written for them.
 */
@ExtendWith(MockitoExtension.class)
class AdvisorApprovedEventConsumerTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AdvisorApprovedEventConsumer consumer;

    @Test
    @DisplayName("promotes a USER to ADVISOR")
    void promotesUserToAdvisor() {
        User user = user(UserRole.USER);
        given(userRepository.findById(user.getId())).willReturn(Optional.of(user));

        consumer.onAdvisorApproved(approvedEvent(user.getId()));

        assertThat(capturePersistedUser().getRole()).isEqualTo(UserRole.ADVISOR);
    }

    /** Kafka is at-least-once, so a redelivery must not produce a second write. */
    @Test
    @DisplayName("a redelivered event for an already-promoted advisor is a no-op")
    void redeliveryIsNoOp() {
        User user = user(UserRole.ADVISOR);
        given(userRepository.findById(user.getId())).willReturn(Optional.of(user));

        consumer.onAdvisorApproved(approvedEvent(user.getId()));

        verify(userRepository, never()).save(any(User.class));
    }

    /**
     * An administrator who also advises must not be quietly stripped of admin rights by an
     * unrelated approval workflow.
     */
    @Test
    @DisplayName("never demotes an ADMIN")
    void doesNotDemoteAdmin() {
        User admin = user(UserRole.ADMIN);
        given(userRepository.findById(admin.getId())).willReturn(Optional.of(admin));

        consumer.onAdvisorApproved(approvedEvent(admin.getId()));

        verify(userRepository, never()).save(any(User.class));
        assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    @DisplayName("ignores an event for a user id that does not exist")
    void ignoresUnknownUser() {
        UUID unknown = UUID.randomUUID();
        given(userRepository.findById(unknown)).willReturn(Optional.empty());

        consumer.onAdvisorApproved(approvedEvent(unknown));

        verify(userRepository, never()).save(any(User.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "not-a-uuid", "12345"})
    @DisplayName("ignores a malformed advisorId rather than throwing into an infinite retry")
    void ignoresMalformedAdvisorId(String advisorId) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "ADVISOR_APPROVED");
        event.put("advisorId", advisorId);

        consumer.onAdvisorApproved(event);

        verify(userRepository, never()).findById(any());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("ignores an event with no advisorId at all")
    void ignoresMissingAdvisorId() {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "ADVISOR_APPROVED");

        consumer.onAdvisorApproved(event);

        verify(userRepository, never()).save(any(User.class));
    }

    /**
     * The published payload carries {@code advisorId} as a string. Accepting a raw {@code UUID}
     * too keeps the consumer working if a future serializer preserves the type.
     */
    @Test
    @DisplayName("accepts advisorId as a UUID object as well as a string")
    void acceptsUuidValuedAdvisorId() {
        User user = user(UserRole.USER);
        given(userRepository.findById(user.getId())).willReturn(Optional.of(user));

        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "ADVISOR_APPROVED");
        event.put("advisorId", user.getId());

        consumer.onAdvisorApproved(event);

        assertThat(capturePersistedUser().getRole()).isEqualTo(UserRole.ADVISOR);
    }

    // ──────────────────────────────────────────────────────────────── helpers

    private static User user(UserRole role) {
        return User.builder()
                .id(UUID.randomUUID())
                .email("someone@example.com")
                .passwordHash("$2a$12$hashed")
                .role(role)
                .build();
    }

    /** Shaped exactly like advisor-service's {@code AdvisorEventPublisher#publishAdvisorApproved}. */
    private static Map<String, Object> approvedEvent(UUID advisorId) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "ADVISOR_APPROVED");
        event.put("advisorId", advisorId.toString());
        event.put("username", "some-advisor");
        event.put("reviewedBy", UUID.randomUUID().toString());
        return event;
    }

    private User capturePersistedUser() {
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        return captor.getValue();
    }
}
