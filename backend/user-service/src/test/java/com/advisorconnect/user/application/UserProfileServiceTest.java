package com.advisorconnect.user.application;

import com.advisorconnect.user.domain.model.UserProfile;
import com.advisorconnect.user.domain.port.out.UserProfileRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    @InjectMocks
    private UserProfileService userProfileService;

    // ─────────────────────────────────────────────────── username generation

    @Nested
    @DisplayName("usernameFromEmail()")
    class UsernameGeneration {

        @ParameterizedTest
        @CsvSource({
                "alice@example.com,          alice",
                "Alice.Smith@Example.COM,    alicesmith",
                "bob+newsletter@example.com, bobnewsletter",
                "j_doe-99@example.com,       jdoe99",
        })
        @DisplayName("lowercases the local part and strips everything non-alphanumeric")
        void derivesFromLocalPart(String email, String expected) {
            assertThat(UserProfileService.usernameFromEmail(email)).isEqualTo(expected);
        }

        @Test
        @DisplayName("never lets the domain leak into the username")
        void ignoresDomain() {
            assertThat(UserProfileService.usernameFromEmail("alice@example.com"))
                    .doesNotContain("example")
                    .isEqualTo("alice");
        }

        @Test
        @DisplayName("falls back to a placeholder when the local part has no usable characters")
        void fallsBackWhenLocalPartIsUnusable() {
            assertThat(UserProfileService.usernameFromEmail("+++@example.com")).isEqualTo("user");
        }

        @Test
        @DisplayName("tolerates null and malformed input instead of throwing mid-signup")
        void toleratesMalformedInput() {
            assertThat(UserProfileService.usernameFromEmail(null)).isEqualTo("user");
            assertThat(UserProfileService.usernameFromEmail("")).isEqualTo("user");
            assertThat(UserProfileService.usernameFromEmail("no-at-sign")).isEqualTo("noatsign");
        }

        @Test
        @DisplayName("caps the generated username at 30 characters")
        void capsLength() {
            String long_ = "a".repeat(80) + "@example.com";
            assertThat(UserProfileService.usernameFromEmail(long_)).hasSize(30);
        }
    }

    // ──────────────────────────────────────────────────── profile creation

    @Nested
    @DisplayName("createFromRegistration()")
    class Creation {

        @Test
        @DisplayName("creates a profile keyed to the auth-service user id")
        void createsProfile() {
            UUID userId = UUID.randomUUID();
            given(userProfileRepository.findById(userId)).willReturn(Optional.empty());
            given(userProfileRepository.existsByUsername("alice")).willReturn(false);
            given(userProfileRepository.save(any(UserProfile.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            userProfileService.createFromRegistration(userId, "alice@example.com", "alice");

            UserProfile saved = capturePersisted();
            assertThat(saved.getId()).isEqualTo(userId);
            assertThat(saved.getEmail()).isEqualTo("alice@example.com");
            assertThat(saved.getUsername()).isEqualTo("alice");
        }

        @Test
        @DisplayName("appends a numeric suffix when the desired username is taken")
        void suffixesOnCollision() {
            UUID userId = UUID.randomUUID();
            given(userProfileRepository.findById(userId)).willReturn(Optional.empty());
            given(userProfileRepository.existsByUsername("alice")).willReturn(true);
            given(userProfileRepository.existsByUsername("alice2")).willReturn(false);
            given(userProfileRepository.save(any(UserProfile.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            userProfileService.createFromRegistration(userId, "alice@other.com", "alice");

            assertThat(capturePersisted().getUsername()).isEqualTo("alice2");
        }

        @Test
        @DisplayName("keeps incrementing the suffix past a run of collisions")
        void suffixesRepeatedly() {
            UUID userId = UUID.randomUUID();
            given(userProfileRepository.findById(userId)).willReturn(Optional.empty());
            given(userProfileRepository.existsByUsername(anyString())).willReturn(true);
            given(userProfileRepository.existsByUsername("alice4")).willReturn(false);
            given(userProfileRepository.save(any(UserProfile.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            userProfileService.createFromRegistration(userId, "alice@other.com", "alice");

            assertThat(capturePersisted().getUsername()).isEqualTo("alice4");
        }

        /**
         * Kafka delivery is at-least-once and the consumer reads from {@code earliest}, so this
         * path is hit in normal operation, not just in failure scenarios.
         */
        @Test
        @DisplayName("is a no-op when a profile for that id already exists")
        void idempotent() {
            UUID userId = UUID.randomUUID();
            UserProfile existing = profile(userId, "chosen-by-the-user");
            given(userProfileRepository.findById(userId)).willReturn(Optional.of(existing));

            UserProfile result = userProfileService.createFromRegistration(
                    userId, "alice@example.com", "alice");

            assertThat(result).isSameAs(existing);
            // Critically it must not overwrite a username the user has since edited.
            assertThat(result.getUsername()).isEqualTo("chosen-by-the-user");
            verify(userProfileRepository, never()).save(any(UserProfile.class));
        }
    }

    // ────────────────────────────────────────────────────────────── reads

    @Test
    @DisplayName("getById() returns empty rather than throwing for an unknown id")
    void getByIdReturnsEmpty() {
        UUID userId = UUID.randomUUID();
        given(userProfileRepository.findById(userId)).willReturn(Optional.empty());

        assertThat(userProfileService.getById(userId)).isEmpty();
    }

    @Test
    @DisplayName("getOrThrow() raises UserProfileNotFoundException for an unknown id")
    void getOrThrowThrows() {
        UUID userId = UUID.randomUUID();
        given(userProfileRepository.findById(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userProfileService.getOrThrow(userId))
                .isInstanceOf(UserProfileNotFoundException.class)
                .hasMessageContaining(userId.toString());
    }

    // ───────────────────────────────────────────────────────────── updates

    @Nested
    @DisplayName("updateProfile()")
    class Updates {

        /**
         * The old controller mutated a managed entity with no transaction around it, so with
         * open-session-in-view nothing ever flushed the change. Asserting the save explicitly is
         * the regression guard.
         */
        @Test
        @DisplayName("persists the change rather than relying on incidental dirty checking")
        void persistsChanges() {
            UUID userId = UUID.randomUUID();
            UserProfile existing = profile(userId, "alice");
            given(userProfileRepository.findById(userId)).willReturn(Optional.of(existing));
            given(userProfileRepository.save(any(UserProfile.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            userProfileService.updateProfile(userId, "Alice A.", "Hello");

            UserProfile saved = capturePersisted();
            assertThat(saved.getDisplayName()).isEqualTo("Alice A.");
            assertThat(saved.getBio()).isEqualTo("Hello");
        }

        @Test
        @DisplayName("treats a null field as 'leave unchanged'")
        void nullMeansUnchanged() {
            UUID userId = UUID.randomUUID();
            UserProfile existing = profile(userId, "alice");
            existing.setDisplayName("Original");
            existing.setBio("Original bio");
            given(userProfileRepository.findById(userId)).willReturn(Optional.of(existing));
            given(userProfileRepository.save(any(UserProfile.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            userProfileService.updateProfile(userId, null, "New bio");

            UserProfile saved = capturePersisted();
            assertThat(saved.getDisplayName()).isEqualTo("Original");
            assertThat(saved.getBio()).isEqualTo("New bio");
        }

        @Test
        @DisplayName("throws for a user with no profile")
        void throwsWhenMissing() {
            UUID userId = UUID.randomUUID();
            given(userProfileRepository.findById(userId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userProfileService.updateProfile(userId, "x", "y"))
                    .isInstanceOf(UserProfileNotFoundException.class);

            verify(userProfileRepository, never()).save(any(UserProfile.class));
        }

        @Test
        @DisplayName("does not let a caller rewrite their username or email through this path")
        void cannotChangeIdentityFields() {
            UUID userId = UUID.randomUUID();
            UserProfile existing = profile(userId, "alice");
            given(userProfileRepository.findById(userId)).willReturn(Optional.of(existing));
            given(userProfileRepository.save(any(UserProfile.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            userProfileService.updateProfile(userId, "Alice A.", "Hello");

            UserProfile saved = capturePersisted();
            assertThat(saved.getUsername()).isEqualTo("alice");
            assertThat(saved.getEmail()).isEqualTo("alice@example.com");
        }
    }

    // ────────────────────────────────────────────────────────────── helpers

    private static UserProfile profile(UUID id, String username) {
        UserProfile profile = new UserProfile();
        profile.setId(id);
        profile.setUsername(username);
        profile.setEmail("alice@example.com");
        return profile;
    }

    private UserProfile capturePersisted() {
        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileRepository).save(captor.capture());
        return captor.getValue();
    }
}
