package com.advisorconnect.auth.application;

import com.advisorconnect.auth.adapter.in.web.dto.*;
import com.advisorconnect.auth.adapter.out.messaging.AuthEventPublisher;
import com.advisorconnect.auth.domain.model.User;
import com.advisorconnect.auth.domain.model.UserRole;
import com.advisorconnect.auth.domain.port.in.AuthUseCase;
import com.advisorconnect.auth.domain.port.out.UserRepository;
import com.advisorconnect.auth.infrastructure.security.JwtTokenProvider;
import com.advisorconnect.auth.infrastructure.security.RefreshTokenStore;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService implements AuthUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenStore refreshTokenStore;
    private final AuthEventPublisher eventPublisher;

    /**
     * {@inheritDoc}
     *
     * <p>The role is fixed to {@link UserRole#USER} here and is <strong>never</strong> read from
     * the request. {@code RegisterRequest} used to carry a {@code role} field, which made the
     * privilege level client-supplied: {@code POST /auth/register {"role":"ADMIN"}} minted an
     * administrator for anyone who asked. Elevated accounts now come only from the admin seeder
     * or from {@link #createAdminUser}, and {@code ADVISOR} only from an approved application.
     */
    @Override
    public TokenResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered");
        }
        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(UserRole.USER)
                .build();
        user = userRepository.save(user);

        // Downstream services provision their own view of the account from this event — notably
        // user-service, which creates the UserProfile row backing GET /users/me.
        eventPublisher.publishUserRegistered(user.getId(), user.getEmail());

        return generateTokenResponse(user);
    }

    @Override
    public UUID createAdminUser(CreateAdminRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered");
        }
        User admin = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(UserRole.ADMIN)
                .build();
        admin = userRepository.save(admin);

        // No user.registered event: that event means "a new end user exists, give them a public
        // profile". An administrator is a back-office account, not a directory entry, and
        // publishing here would have user-service mint a public username for it.
        return admin.getId();
    }

    @Override
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
        return generateTokenResponse(user);
    }

    @Override
    public TokenResponse refresh(String refreshToken) {
        var userId = refreshTokenStore.validate(refreshToken)
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("User not found"));
        refreshTokenStore.revoke(refreshToken);
        return generateTokenResponse(user);
    }

    @Override
    public void logout(String refreshToken) {
        refreshTokenStore.revoke(refreshToken);
    }

    private TokenResponse generateTokenResponse(User user) {
        String accessToken  = jwtTokenProvider.generateAccessToken(user.getId().toString(), user.getRole().name());
        String refreshToken = jwtTokenProvider.generateRefreshToken();
        refreshTokenStore.store(refreshToken, user.getId());
        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .role(user.getRole().name())
                // Derived from the configured jwt.access-token-expiry-ms rather than hardcoded, so
                // the value clients cache against always matches the token actually issued.
                .expiresIn(jwtTokenProvider.getAccessTokenExpiryMs() / 1000)
                .build();
    }
}
