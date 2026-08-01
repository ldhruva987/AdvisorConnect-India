package com.advisorconnect.auth.domain.port.in;

import com.advisorconnect.auth.adapter.in.web.dto.LoginRequest;
import com.advisorconnect.auth.adapter.in.web.dto.RegisterRequest;
import com.advisorconnect.auth.adapter.in.web.dto.TokenResponse;

/**
 * Input port (use case interface) — defines what the application can do.
 * Part of the Ports & Adapters (Hexagonal) architecture.
 */
public interface AuthUseCase {
    TokenResponse register(RegisterRequest request);
    TokenResponse login(LoginRequest request);
    TokenResponse refresh(String refreshToken);
    void logout(String refreshToken);
}
