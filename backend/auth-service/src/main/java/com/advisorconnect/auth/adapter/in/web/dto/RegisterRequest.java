package com.advisorconnect.auth.adapter.in.web.dto;

import com.advisorconnect.auth.domain.model.UserRole;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Size(min = 8, max = 100)
    private String password;

    @NotNull
    private UserRole role;
}
