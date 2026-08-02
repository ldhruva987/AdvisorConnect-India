package com.advisorconnect.user.adapter.in.web;

import com.advisorconnect.user.application.UserProfileNotFoundException;
import com.advisorconnect.user.application.UserProfileService;
import com.advisorconnect.user.config.SecurityConfig;
import com.advisorconnect.user.domain.model.UserProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract tests for the three endpoints, written to hold across the move off the raw
 * {@code EntityManager}: same paths, same JSON, same 404 on a missing profile. If the refactor
 * changed any of that from the caller's point of view, these fail.
 */
@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class UserControllerTest {

    private static final String USER_ID = "3f7c1c4e-2b8a-4a1d-9f2e-5c6d7e8f9a0b";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserProfileService userProfileService;

    // ─────────────────────────────────────────────────────── GET /users/{id}

    @Test
    @DisplayName("GET /users/{id} returns the profile as JSON and stays public")
    void getProfileById() throws Exception {
        UUID id = UUID.fromString(USER_ID);
        given(userProfileService.getOrThrow(id)).willReturn(profile(id, "alice"));

        mockMvc.perform(get("/users/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID))
                .andExpect(jsonPath("$.username").value("alice"));
    }

    @Test
    @DisplayName("GET /users/{id} 404s for an unknown id")
    void getProfileByIdNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        given(userProfileService.getOrThrow(id)).willThrow(new UserProfileNotFoundException(id));

        mockMvc.perform(get("/users/{id}", id))
                .andExpect(status().isNotFound());
    }

    // ──────────────────────────────────────────────────────── GET /users/me

    /**
     * The behaviour Phase 2 exists to fix. It is asserted here at the controller level and again
     * end to end, against a real database and a real Kafka event, in {@code UserServiceIT}.
     */
    @Test
    @DisplayName("GET /users/me returns the caller's own profile")
    void getMyProfile() throws Exception {
        UUID id = UUID.fromString(USER_ID);
        given(userProfileService.getOrThrow(id)).willReturn(profile(id, "alice"));

        mockMvc.perform(get("/users/me")
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    @DisplayName("GET /users/me requires authentication")
    void getMyProfileRequiresAuth() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().is(anyOf401Or403()));

        verifyNoInteractions(userProfileService);
    }

    @Test
    @DisplayName("GET /users/me with a non-UUID identity header fails closed")
    void getMyProfileMalformedIdentity() throws Exception {
        mockMvc.perform(get("/users/me")
                        .header("X-User-Id", "not-a-uuid")
                        .header("X-User-Role", "USER"))
                .andExpect(status().is(anyOf401Or403()));

        verifyNoInteractions(userProfileService);
    }

    @Test
    @DisplayName("GET /users/me still 404s when the caller genuinely has no profile")
    void getMyProfileNotFound() throws Exception {
        UUID id = UUID.fromString(USER_ID);
        given(userProfileService.getOrThrow(id)).willThrow(new UserProfileNotFoundException(id));

        mockMvc.perform(get("/users/me")
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "USER"))
                .andExpect(status().isNotFound());
    }

    // ──────────────────────────────────────────────────────── PUT /users/me

    @Test
    @DisplayName("PUT /users/me applies the update and echoes the saved profile")
    void updateProfile() throws Exception {
        UUID id = UUID.fromString(USER_ID);
        UserProfile updated = profile(id, "alice");
        updated.setDisplayName("Alice A.");
        updated.setBio("Hello");
        given(userProfileService.updateProfile(id, "Alice A.", "Hello")).willReturn(updated);

        mockMvc.perform(put("/users/me")
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Alice A.","bio":"Hello"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Alice A."))
                .andExpect(jsonPath("$.bio").value("Hello"));

        verify(userProfileService).updateProfile(id, "Alice A.", "Hello");
    }

    @Test
    @DisplayName("PUT /users/me passes null through for an omitted field (partial update)")
    void partialUpdate() throws Exception {
        UUID id = UUID.fromString(USER_ID);
        given(userProfileService.updateProfile(eq(id), isNull(), eq("Just the bio")))
                .willReturn(profile(id, "alice"));

        mockMvc.perform(put("/users/me")
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bio":"Just the bio"}"""))
                .andExpect(status().isOk());

        verify(userProfileService).updateProfile(id, null, "Just the bio");
    }

    @Test
    @DisplayName("PUT /users/me requires authentication")
    void updateRequiresAuth() throws Exception {
        mockMvc.perform(put("/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bio":"anonymous edit"}"""))
                .andExpect(status().is(anyOf401Or403()));

        verifyNoInteractions(userProfileService);
    }

    @Test
    @DisplayName("PUT /users/me 404s when the caller has no profile")
    void updateNotFound() throws Exception {
        UUID id = UUID.fromString(USER_ID);
        willThrow(new UserProfileNotFoundException(id))
                .given(userProfileService).updateProfile(eq(id), any(), any());

        mockMvc.perform(put("/users/me")
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bio":"Hello"}"""))
                .andExpect(status().isNotFound());
    }

    // ──────────────────────────────────────────────────────────── helpers

    private static UserProfile profile(UUID id, String username) {
        UserProfile profile = new UserProfile();
        profile.setId(id);
        profile.setUsername(username);
        profile.setEmail("alice@example.com");
        return profile;
    }

    private static org.hamcrest.Matcher<Integer> anyOf401Or403() {
        return org.hamcrest.Matchers.isOneOf(401, 403);
    }
}
