package com.advisorconnect.auth.adapter.in.web;

import com.advisorconnect.auth.adapter.in.web.dto.*;
import com.advisorconnect.auth.domain.port.in.AuthUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthUseCase authUseCase;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse register(@Valid @RequestBody RegisterRequest request) {
        return authUseCase.register(request);
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authUseCase.login(request);
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@RequestBody RefreshRequest request) {
        return authUseCase.refresh(request.getRefreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestBody RefreshRequest request) {
        authUseCase.logout(request.getRefreshToken());
    }

    /**
     * Provisions a further administrator. This is the only ongoing path to an {@code ADMIN}
     * account once the env-gated seeder has bootstrapped the first one — {@code /auth/register}
     * always creates a plain {@code USER}.
     *
     * <p>Guarded twice on purpose: {@code SecurityConfig} refuses {@code /auth/admin/**} to
     * non-admins at the filter chain, and this annotation refuses it again at the method. The
     * surrounding {@code /auth/**} rule is {@code permitAll()} by necessity (register/login are
     * public), so a single missed matcher would otherwise silently expose admin creation.
     */
    @PostMapping("/admin/users")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public CreatedUserResponse createAdminUser(@Valid @RequestBody CreateAdminRequest request) {
        return new CreatedUserResponse(authUseCase.createAdminUser(request));
    }
}
