package com.advisorconnect.auth.application;

import com.advisorconnect.auth.adapter.in.web.dto.CreateAdminRequest;
import com.advisorconnect.auth.adapter.in.web.dto.LoginRequest;
import com.advisorconnect.auth.adapter.in.web.dto.RegisterRequest;
import com.advisorconnect.auth.adapter.in.web.dto.TokenResponse;
import com.advisorconnect.auth.adapter.out.messaging.AuthEventPublisher;
import com.advisorconnect.auth.domain.model.User;
import com.advisorconnect.auth.domain.model.UserRole;
import com.advisorconnect.auth.domain.port.out.UserRepository;
import com.advisorconnect.auth.infrastructure.security.JwtTokenProvider;
import com.advisorconnect.auth.infrastructure.security.RefreshTokenStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Two independent regressions are pinned here.
 *
 * <p><strong>Token expiry.</strong> {@code expiresIn} used to be the hardcoded literal
 * {@code 900}, so any change to {@code jwt.access-token-expiry-ms} would silently desynchronise
 * what clients were told from when the token actually expired. Those tests mock a deliberately
 * non-default expiry — a hardcoded literal cannot pass them.
 *
 * <p><strong>Privilege escalation.</strong> {@code RegisterRequest} used to carry a {@code role}
 * field that {@code register()} passed straight through to the persisted entity, so
 * {@code POST /auth/register {"role":"ADMIN"}} minted an administrator for anyone who asked. The
 * {@code Registration} tests below assert the persisted role is always {@code USER}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    /** Deliberately not 900_000ms, so a hardcoded 900 cannot accidentally satisfy the assertion. */
    private static final long TEST_EXPIRY_MS = 123_000L;

    @Mock
    private UserRepository userRepository;

    @Mock
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenStore refreshTokenStore;

    @Mock
    private AuthEventPublisher eventPublisher;

    @InjectMocks
    private AuthService authService;

    private User existingUser;

    @BeforeEach
    void setUp() {
        existingUser = User.builder()
                .id(UUID.randomUUID())
                .email("advisor@example.com")
                .passwordHash("$2a$12$hashed")
                .role(UserRole.USER)
                .build();

        given(jwtTokenProvider.getAccessTokenExpiryMs()).willReturn(TEST_EXPIRY_MS);
        given(jwtTokenProvider.generateAccessToken(anyString(), anyString())).willReturn("access-token");
        given(jwtTokenProvider.generateRefreshToken()).willReturn("refresh-token");
    }

    // ───────────────────────────────────────────────────────────── token expiry

    @Test
    @DisplayName("register() reports expiresIn derived from the configured token expiry")
    void registerReportsConfiguredExpiry() {
        givenRegistrationSucceeds();

        TokenResponse response = authService.register(registerRequest("new@example.com"));

        assertThat(response.getExpiresIn()).isEqualTo(TEST_EXPIRY_MS / 1000);
    }

    @Test
    @DisplayName("login() reports expiresIn derived from the configured token expiry")
    void loginReportsConfiguredExpiry() {
        givenLoginSucceeds();

        TokenResponse response = authService.login(loginRequest());

        assertThat(response.getExpiresIn()).isEqualTo(TEST_EXPIRY_MS / 1000);
    }

    @Test
    @DisplayName("refresh() reports expiresIn derived from the configured token expiry")
    void refreshReportsConfiguredExpiry() {
        given(refreshTokenStore.validate("old-refresh")).willReturn(Optional.of(existingUser.getId()));
        given(userRepository.findById(existingUser.getId())).willReturn(Optional.of(existingUser));

        TokenResponse response = authService.refresh("old-refresh");

        assertThat(response.getExpiresIn()).isEqualTo(TEST_EXPIRY_MS / 1000);
    }

    @Test
    @DisplayName("expiresIn tracks the configured value rather than any fixed literal")
    void expiresInTracksWhateverIsConfigured() {
        givenLoginSucceeds();
        given(jwtTokenProvider.getAccessTokenExpiryMs()).willReturn(3_600_000L);

        assertThat(authService.login(loginRequest()).getExpiresIn()).isEqualTo(3600);
    }

    @Test
    @DisplayName("The production default of 900000ms still yields the historical 900 seconds")
    void productionDefaultPreservesExistingBehaviour() {
        givenLoginSucceeds();
        given(jwtTokenProvider.getAccessTokenExpiryMs()).willReturn(900_000L);

        assertThat(authService.login(loginRequest()).getExpiresIn()).isEqualTo(900);
    }

    @Test
    @DisplayName("The issued refresh token is persisted against the user")
    void refreshTokenIsStored() {
        givenLoginSucceeds();

        TokenResponse response = authService.login(loginRequest());

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(response.getUserId()).isEqualTo(existingUser.getId());
        verify(refreshTokenStore).store("refresh-token", existingUser.getId());
    }

    // ─────────────────────────────────────────────── registration is locked to USER

    @Nested
    @DisplayName("register()")
    class Registration {

        @Test
        @DisplayName("always persists role=USER")
        void alwaysPersistsUserRole() {
            givenRegistrationSucceeds();

            authService.register(registerRequest("new@example.com"));

            assertThat(capturePersistedUser().getRole()).isEqualTo(UserRole.USER);
        }

        /**
         * The DTO has no {@code role} setter any more, so a client cannot express a role at all.
         * This forces one onto the request object reflectively — simulating the field being
         * reintroduced — and asserts the service still ignores it. That is the property that
         * actually matters: {@code register()} must not read the role from input, whatever the
         * input happens to carry.
         */
        @ParameterizedTest
        @EnumSource(UserRole.class)
        @DisplayName("ignores any role smuggled onto the request, including ADMIN")
        void ignoresSmuggledRole(UserRole smuggled) throws Exception {
            givenRegistrationSucceeds();

            RegisterRequest request = registerRequest("escalate@example.com");
            trySetField(request, "role", smuggled);

            authService.register(request);

            assertThat(capturePersistedUser().getRole()).isEqualTo(UserRole.USER);
        }

        @Test
        @DisplayName("hashes the password rather than storing it verbatim")
        void hashesPassword() {
            givenRegistrationSucceeds();

            authService.register(registerRequest("new@example.com"));

            User persisted = capturePersistedUser();
            assertThat(persisted.getPasswordHash()).isEqualTo("$2a$12$hashed");
            assertThat(persisted.getPasswordHash()).isNotEqualTo("password123");
        }

        @Test
        @DisplayName("publishes user.registered with the persisted id and email")
        void publishesUserRegistered() {
            givenRegistrationSucceeds();

            authService.register(registerRequest("new@example.com"));

            // Deliberately the *saved* user's id, not anything from the request: the id is
            // database-generated, and user-service keys its profile row on it.
            verify(eventPublisher).publishUserRegistered(existingUser.getId(), existingUser.getEmail());
        }

        @Test
        @DisplayName("publishes nothing when the email is already registered")
        void publishesNothingOnDuplicateEmail() {
            given(userRepository.existsByEmail("taken@example.com")).willReturn(true);

            assertThatThrownBy(() -> authService.register(registerRequest("taken@example.com")))
                    .isInstanceOf(IllegalArgumentException.class);

            verifyNoInteractions(eventPublisher);
            verify(userRepository, never()).save(any(User.class));
        }
    }

    // ───────────────────────────────────────────────────────── admin provisioning

    @Nested
    @DisplayName("createAdminUser()")
    class AdminProvisioning {

        @Test
        @DisplayName("persists role=ADMIN and returns the new id")
        void persistsAdminRole() {
            User saved = User.builder()
                    .id(UUID.randomUUID())
                    .email("admin@example.com")
                    .passwordHash("$2a$12$hashed")
                    .role(UserRole.ADMIN)
                    .build();
            given(userRepository.existsByEmail("admin@example.com")).willReturn(false);
            given(passwordEncoder.encode("password123")).willReturn("$2a$12$hashed");
            given(userRepository.save(any(User.class))).willReturn(saved);

            UUID id = authService.createAdminUser(createAdminRequest("admin@example.com"));

            assertThat(id).isEqualTo(saved.getId());
            assertThat(capturePersistedUser().getRole()).isEqualTo(UserRole.ADMIN);
        }

        /**
         * {@code user.registered} means "a new end user exists, give them a public profile".
         * Emitting it for a back-office account would have user-service mint a public directory
         * username for an administrator.
         */
        @Test
        @DisplayName("does not publish user.registered for a back-office account")
        void doesNotPublishUserRegistered() {
            given(userRepository.existsByEmail("admin@example.com")).willReturn(false);
            given(passwordEncoder.encode("password123")).willReturn("$2a$12$hashed");
            given(userRepository.save(any(User.class))).willReturn(existingUser);

            authService.createAdminUser(createAdminRequest("admin@example.com"));

            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("rejects an email that is already registered")
        void rejectsDuplicateEmail() {
            given(userRepository.existsByEmail("taken@example.com")).willReturn(true);

            assertThatThrownBy(() -> authService.createAdminUser(createAdminRequest("taken@example.com")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already registered");

            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("issues no tokens — creating an account must not hand the creator a session for it")
        void issuesNoSession() {
            given(userRepository.existsByEmail("admin@example.com")).willReturn(false);
            given(passwordEncoder.encode("password123")).willReturn("$2a$12$hashed");
            given(userRepository.save(any(User.class))).willReturn(existingUser);

            authService.createAdminUser(createAdminRequest("admin@example.com"));

            verifyNoInteractions(refreshTokenStore);
            verify(jwtTokenProvider, never()).generateAccessToken(anyString(), anyString());
        }
    }

    // ────────────────────────────────────────────────────────────────── helpers

    private void givenRegistrationSucceeds() {
        given(userRepository.existsByEmail(anyString())).willReturn(false);
        given(passwordEncoder.encode("password123")).willReturn("$2a$12$hashed");
        given(userRepository.save(any(User.class))).willReturn(existingUser);
    }

    private void givenLoginSucceeds() {
        given(userRepository.findByEmail("advisor@example.com")).willReturn(Optional.of(existingUser));
        given(passwordEncoder.matches("password123", "$2a$12$hashed")).willReturn(true);
        given(userRepository.save(any(User.class))).willReturn(existingUser);
    }

    private User capturePersistedUser() {
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        return captor.getValue();
    }

    private static RegisterRequest registerRequest(String email) {
        RegisterRequest request = new RegisterRequest();
        request.setEmail(email);
        request.setPassword("password123");
        return request;
    }

    private static CreateAdminRequest createAdminRequest(String email) {
        CreateAdminRequest request = new CreateAdminRequest();
        request.setEmail(email);
        request.setPassword("password123");
        return request;
    }

    private static LoginRequest loginRequest() {
        LoginRequest request = new LoginRequest();
        request.setEmail("advisor@example.com");
        request.setPassword("password123");
        return request;
    }

    /** No-op when the field does not exist — which is the current, desired state of the DTO. */
    private static void trySetField(Object target, String fieldName, Object value) throws Exception {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (NoSuchFieldException expected) {
            // RegisterRequest carries no role field, which is exactly the point.
        }
    }
}
