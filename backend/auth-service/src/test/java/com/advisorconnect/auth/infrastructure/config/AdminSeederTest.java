package com.advisorconnect.auth.infrastructure.config;

import com.advisorconnect.auth.domain.model.User;
import com.advisorconnect.auth.domain.model.UserRole;
import com.advisorconnect.auth.domain.port.out.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * The seeder is the only way the first administrator can come into existence, which makes it both
 * necessary and dangerous. These tests pin the three properties that keep it safe: it does nothing
 * unless explicitly configured, it does nothing when an administrator already exists, and it never
 * rewrites an account that is already there.
 */
@ExtendWith(MockitoExtension.class)
class AdminSeederTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AdminSeeder adminSeeder;

    // ───────────────────────────────────────────────────────────── opt-in gating

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("does nothing when the seed email is unset — the default in every environment")
    void noOpWhenEmailUnset(String email) {
        configure(email, "password123");

        adminSeeder.run(null);

        verify(userRepository, never()).save(any(User.class));
        verify(userRepository, never()).existsByRole(any());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("does nothing when the seed password is unset")
    void noOpWhenPasswordUnset(String password) {
        configure("admin@example.com", password);

        adminSeeder.run(null);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("refuses to seed an administrator behind a password shorter than 8 characters")
    void refusesWeakPassword() {
        configure("admin@example.com", "short");

        adminSeeder.run(null);

        verify(userRepository, never()).save(any(User.class));
    }

    // ──────────────────────────────────────────────────────────────── happy path

    @Test
    @DisplayName("seeds an ADMIN with a hashed password when configured and none exists")
    void seedsAdmin() {
        configure("admin@example.com", "password123");
        given(userRepository.existsByRole(UserRole.ADMIN)).willReturn(false);
        given(userRepository.existsByEmail("admin@example.com")).willReturn(false);
        given(passwordEncoder.encode("password123")).willReturn("$2a$12$hashed");
        given(userRepository.save(any(User.class)))
                .willAnswer(inv -> withId(inv.getArgument(0)));

        adminSeeder.run(null);

        User seeded = capturePersistedUser();
        assertThat(seeded.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(seeded.getEmail()).isEqualTo("admin@example.com");
        assertThat(seeded.getPasswordHash()).isEqualTo("$2a$12$hashed");
        assertThat(seeded.getPasswordHash()).isNotEqualTo("password123");
    }

    @Test
    @DisplayName("trims surrounding whitespace off the configured email")
    void trimsEmail() {
        configure("  admin@example.com  ", "password123");
        given(userRepository.existsByRole(UserRole.ADMIN)).willReturn(false);
        given(userRepository.existsByEmail("admin@example.com")).willReturn(false);
        given(passwordEncoder.encode("password123")).willReturn("$2a$12$hashed");
        given(userRepository.save(any(User.class)))
                .willAnswer(inv -> withId(inv.getArgument(0)));

        adminSeeder.run(null);

        assertThat(capturePersistedUser().getEmail()).isEqualTo("admin@example.com");
    }

    // ────────────────────────────────────────────────────────────── idempotency

    @Test
    @DisplayName("is a no-op on restart once any administrator exists")
    void idempotentAcrossRestarts() {
        configure("admin@example.com", "password123");
        given(userRepository.existsByRole(UserRole.ADMIN)).willReturn(true);

        adminSeeder.run(null);

        verify(userRepository, never()).save(any(User.class));
    }

    /**
     * Running the seeder twice in one process stands in for the real-world case: the variables
     * are left set in the deployment and the service restarts. The second pass must not create a
     * second administrator.
     */
    @Test
    @DisplayName("running twice creates exactly one administrator")
    void secondRunCreatesNothing() {
        configure("admin@example.com", "password123");
        given(userRepository.existsByRole(UserRole.ADMIN)).willReturn(false, true);
        given(userRepository.existsByEmail("admin@example.com")).willReturn(false);
        given(passwordEncoder.encode("password123")).willReturn("$2a$12$hashed");
        given(userRepository.save(any(User.class)))
                .willAnswer(inv -> withId(inv.getArgument(0)));

        adminSeeder.run(null);
        adminSeeder.run(null);

        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("declines to touch an existing account rather than silently promoting it to ADMIN")
    void doesNotPromoteExistingAccount() {
        configure("someone@example.com", "password123");
        given(userRepository.existsByRole(UserRole.ADMIN)).willReturn(false);
        given(userRepository.existsByEmail("someone@example.com")).willReturn(true);

        adminSeeder.run(null);

        verify(userRepository, never()).save(any(User.class));
    }

    // ──────────────────────────────────────────────────────────────────  helpers

    private void configure(String email, String password) {
        ReflectionTestUtils.setField(adminSeeder, "seedEmail", email);
        ReflectionTestUtils.setField(adminSeeder, "seedPassword", password);
    }

    private User capturePersistedUser() {
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        return captor.getValue();
    }

    /** Mimics the database assigning a generated id on insert. */
    private static User withId(User user) {
        user.setId(UUID.randomUUID());
        return user;
    }
}
