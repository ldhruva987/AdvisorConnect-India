package com.advisorconnect.auth.adapter.in.web.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * Payload for {@code POST /auth/admin/users} — provisioning of a further administrator by an
 * existing administrator. Same shape as {@link RegisterRequest}; the role is not accepted from the
 * client here either, it is fixed to {@code ADMIN} by the endpoint itself.
 */
@Data
public class CreateAdminRequest {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Size(min = 8, max = 100)
    private String password;
}
