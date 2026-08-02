package com.advisorconnect.auth.adapter.in.web.dto;

import java.util.UUID;

/**
 * Response for {@code POST /auth/admin/users}.
 *
 * <p>Deliberately carries the new account's id and nothing else — in particular no tokens. The
 * caller is a <em>different</em> administrator provisioning an account for someone else; handing
 * them a live session for it would turn account creation into account impersonation.
 */
public record CreatedUserResponse(UUID userId) {
}
