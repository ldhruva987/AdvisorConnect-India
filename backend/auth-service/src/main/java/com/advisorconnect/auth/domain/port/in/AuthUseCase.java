package com.advisorconnect.auth.domain.port.in;

import com.advisorconnect.auth.adapter.in.web.dto.CreateAdminRequest;
import com.advisorconnect.auth.adapter.in.web.dto.LoginRequest;
import com.advisorconnect.auth.adapter.in.web.dto.RegisterRequest;
import com.advisorconnect.auth.adapter.in.web.dto.TokenResponse;

import java.util.UUID;

/**
 * Input port (use case interface) — defines what the application can do.
 * Part of the Ports & Adapters (Hexagonal) architecture.
 */
public interface AuthUseCase {
    TokenResponse register(RegisterRequest request);
    TokenResponse login(LoginRequest request);
    TokenResponse refresh(String refreshToken);
    void logout(String refreshToken);

    /**
     * Provisions a new {@code ADMIN} account. Deliberately returns only the new user's id and
     * issues no tokens: the caller is a different administrator, and handing them a session for
     * the account they just created would be a privilege-transfer waiting to happen.
     */
    UUID createAdminUser(CreateAdminRequest request);
}
