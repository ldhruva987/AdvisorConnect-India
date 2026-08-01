package com.advisorconnect.auth.adapter.in.web.dto;

import lombok.Data;

@Data
public class RefreshRequest {
    private String refreshToken;
}
