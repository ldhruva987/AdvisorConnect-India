package com.advisorconnect.auth.adapter.in.web.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * Self-service registration payload.
 *
 * <p>There is deliberately no {@code role} field. It used to carry one, which meant the role was
 * client-supplied: anybody could POST {@code {"role":"ADMIN"}} to {@code /auth/register} and mint
 * themselves an administrator. Role is now decided server-side ({@code USER}, always) — see
 * {@code AuthService#register}. Admin accounts are created only by the seeder or by an existing
 * admin via {@code POST /auth/admin/users}.
 */
@Data
public class RegisterRequest {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Size(min = 8, max = 100)
    private String password;
}
