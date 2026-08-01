package com.advisorconnect.auth.adapter.in.web.dto;

import lombok.Builder;
import lombok.Data;
import java.util.UUID;

@Data
@Builder
public class TokenResponse {
    private String accessToken;
    private String refreshToken;
    private UUID userId;
    private String role;
    private long expiresIn;
}
