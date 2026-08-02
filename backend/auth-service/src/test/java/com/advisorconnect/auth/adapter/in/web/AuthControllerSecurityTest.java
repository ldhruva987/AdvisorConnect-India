package com.advisorconnect.auth.adapter.in.web;

import com.advisorconnect.auth.adapter.in.web.dto.CreateAdminRequest;
import com.advisorconnect.auth.adapter.in.web.dto.RegisterRequest;
import com.advisorconnect.auth.adapter.in.web.dto.TokenResponse;
import com.advisorconnect.auth.domain.port.in.AuthUseCase;
import com.advisorconnect.auth.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /auth/**} is {@code permitAll()} because registration and login have to be reachable by
 * anonymous callers. That makes admin provisioning, which lives under the same prefix, exactly the
 * kind of endpoint that ships accidentally open — so its two guards (the {@code /auth/admin/**}
 * matcher and the handler's {@code @PreAuthorize}) are asserted here rather than assumed.
 */
@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerSecurityTest {

    private static final String USER_ID = "3f7c1c4e-2b8a-4a1d-9f2e-5c6d7e8f9a0b";
    private static final String ADMIN_ID = "8a1b2c3d-4e5f-4061-8273-849506172839";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthUseCase authUseCase;

    // ─────────────────────────────────────────────────────────── public surface

    @Test
    @DisplayName("POST /auth/register stays public")
    void registerIsPublic() throws Exception {
        given(authUseCase.register(any())).willReturn(TokenResponse.builder().build());

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"new@example.com","password":"password123"}"""))
                .andExpect(status().isCreated());
    }

    /**
     * The original escalation vector, exercised end-to-end over HTTP: a request body that asks
     * for {@code ADMIN}. The DTO no longer has the field, so the value is dropped during binding
     * and never reaches the service. Asserting the request still succeeds matters as much as
     * asserting the role is gone — rejecting it outright would break real clients that still send
     * the old field.
     */
    @Test
    @DisplayName("a role in the registration body is silently dropped, not honoured")
    void registrationBodyCannotCarryARole() throws Exception {
        given(authUseCase.register(any())).willReturn(TokenResponse.builder().build());

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"escalate@example.com","password":"password123","role":"ADMIN"}"""))
                .andExpect(status().isCreated());

        ArgumentCaptor<RegisterRequest> captor = ArgumentCaptor.forClass(RegisterRequest.class);
        verify(authUseCase).register(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("escalate@example.com");

        // There is nowhere for the requested role to have landed: the field is gone from the DTO,
        // and this fails loudly if anyone reintroduces it.
        assertThat(RegisterRequest.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain("role");
    }

    // ────────────────────────────────────────────────── admin provisioning is gated

    @Test
    @DisplayName("POST /auth/admin/users without identity headers is rejected")
    void createAdminWithoutHeadersIsRejected() throws Exception {
        mockMvc.perform(post("/auth/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminBody()))
                .andExpect(status().is(anyOf401Or403()));

        verifyNoInteractions(authUseCase);
    }

    @Test
    @DisplayName("POST /auth/admin/users as a plain USER is forbidden")
    void createAdminAsUserIsForbidden() throws Exception {
        mockMvc.perform(post("/auth/admin/users")
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminBody()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(authUseCase);
    }

    @Test
    @DisplayName("POST /auth/admin/users as an ADVISOR is forbidden")
    void createAdminAsAdvisorIsForbidden() throws Exception {
        mockMvc.perform(post("/auth/admin/users")
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "ADVISOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminBody()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(authUseCase);
    }

    @Test
    @DisplayName("a non-UUID X-User-Id fails closed rather than authenticating as ADMIN")
    void malformedUserIdFailsClosed() throws Exception {
        mockMvc.perform(post("/auth/admin/users")
                        .header("X-User-Id", "not-a-uuid")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminBody()))
                .andExpect(status().is(anyOf401Or403()));

        verifyNoInteractions(authUseCase);
    }

    @Test
    @DisplayName("POST /auth/admin/users as an ADMIN creates the account and returns its id")
    void createAdminAsAdminSucceeds() throws Exception {
        UUID newAdminId = UUID.randomUUID();
        given(authUseCase.createAdminUser(any(CreateAdminRequest.class))).willReturn(newAdminId);

        mockMvc.perform(post("/auth/admin/users")
                        .header("X-User-Id", ADMIN_ID)
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(newAdminId.toString()))
                // No session for the created account — see CreatedUserResponse.
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());

        verify(authUseCase).createAdminUser(any(CreateAdminRequest.class));
    }

    @Test
    @DisplayName("an ADMIN still cannot create an admin with an invalid payload")
    void validationStillApplies() throws Exception {
        mockMvc.perform(post("/auth/admin/users")
                        .header("X-User-Id", ADMIN_ID)
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","password":"short"}"""))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authUseCase);
    }

    // ──────────────────────────────────────────────────────────────── helpers

    private static String adminBody() {
        return """
                {"email":"new-admin@example.com","password":"password123"}""";
    }

    /**
     * Which of the two an unauthenticated request yields is a Spring Security entry-point detail;
     * the property under test is that the request is refused.
     */
    private static org.hamcrest.Matcher<Integer> anyOf401Or403() {
        return org.hamcrest.Matchers.isOneOf(401, 403);
    }
}
