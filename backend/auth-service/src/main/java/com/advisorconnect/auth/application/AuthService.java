package com.advisorconnect.auth.application;

import com.advisorconnect.auth.adapter.in.web.dto.*;
import com.advisorconnect.auth.domain.model.User;
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

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService implements AuthUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenStore refreshTokenStore;

    @Override
    public TokenResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered");
        }
        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .build();
        user = userRepository.save(user);
        return generateTokenResponse(user);
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
                .expiresIn(900)
                .build();
    }
}
