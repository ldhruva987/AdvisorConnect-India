package com.advisorconnect.notification.adapter.in.web;

import com.advisorconnect.notification.application.NotificationService;
import com.advisorconnect.notification.domain.model.Notification;
import com.advisorconnect.notification.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The inbox's HTTP contract, including the first security configuration this service has ever
 * had. Without {@link SecurityConfig} the controller's {@code @PreAuthorize} annotations would
 * enforce nothing and every route would be world-readable.
 */
@WebMvcTest(NotificationController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class NotificationControllerTest {

    private static final String USER_ID = "3f7c1c4e-2b8a-4a1d-9f2e-5c6d7e8f9a0b";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationService notificationService;

    // ------------------------------------------------------------------ the authentication gate

    @Test
    @DisplayName("GET /notifications without identity headers is refused")
    void listWithoutHeadersIsRefused() throws Exception {
        mockMvc.perform(get("/notifications"))
                .andExpect(status().is(anyOf401Or403()));

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("PUT /notifications/{id}/read without identity headers is refused")
    void markReadWithoutHeadersIsRefused() throws Exception {
        mockMvc.perform(put("/notifications/{id}/read", UUID.randomUUID()))
                .andExpect(status().is(anyOf401Or403()));

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("a non-UUID X-User-Id fails closed rather than authenticating")
    void malformedUserIdIsRefused() throws Exception {
        mockMvc.perform(get("/notifications")
                        .header("X-User-Id", "not-a-uuid")
                        .header("X-User-Role", "USER"))
                .andExpect(status().is(anyOf401Or403()));

        verifyNoInteractions(notificationService);
    }

    // ----------------------------------------------------------------------------- listing

    @Test
    @DisplayName("an authenticated user gets their own inbox back")
    void listReturnsTheCallersInbox() throws Exception {
        UUID userId = UUID.fromString(USER_ID);
        UUID notificationId = UUID.randomUUID();
        given(notificationService.listForUser(eq(userId), any(Pageable.class)))
                .willReturn(page(notification(notificationId, userId, false)));

        mockMvc.perform(get("/notifications")
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(notificationId.toString()))
                .andExpect(jsonPath("$.content[0].type").value("BOOKING_CONFIRMED"))
                .andExpect(jsonPath("$.content[0].title").value("Booking confirmed"))
                .andExpect(jsonPath("$.content[0].read").value(false))
                // The caller's own id is not echoed back; every response is already scoped to it.
                .andExpect(jsonPath("$.content[0].userId").doesNotExist());
    }

    @Test
    @DisplayName("the recipient comes from the header, never from a query parameter")
    void listIgnoresAnAttackerSuppliedUserId() throws Exception {
        UUID caller = UUID.fromString(USER_ID);
        UUID victim = UUID.randomUUID();
        given(notificationService.listForUser(eq(caller), any(Pageable.class)))
                .willReturn(page());

        mockMvc.perform(get("/notifications")
                        .param("userId", victim.toString())
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "USER"))
                .andExpect(status().isOk());

        verify(notificationService).listForUser(eq(caller), any(Pageable.class));
    }

    @Test
    @DisplayName("page and size are passed through to the service")
    void paginationIsHonoured() throws Exception {
        UUID userId = UUID.fromString(USER_ID);
        given(notificationService.listForUser(eq(userId), any(Pageable.class)))
                .willReturn(page());

        mockMvc.perform(get("/notifications")
                        .param("page", "2")
                        .param("size", "5")
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "USER"))
                .andExpect(status().isOk());

        verify(notificationService).listForUser(userId, PageRequest.of(2, 5));
    }

    // --------------------------------------------------------------------------- mark read

    @Test
    @DisplayName("an authenticated user can mark their own notification read")
    void markReadSucceeds() throws Exception {
        UUID userId = UUID.fromString(USER_ID);
        UUID notificationId = UUID.randomUUID();
        given(notificationService.markRead(notificationId, userId))
                .willReturn(notification(notificationId, userId, true));

        mockMvc.perform(put("/notifications/{id}/read", notificationId)
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(notificationId.toString()))
                .andExpect(jsonPath("$.read").value(true));
    }

    @Test
    @DisplayName("marking somebody else's notification read is 403, not a 500")
    void markReadOnSomeoneElsesNotificationIsForbidden() throws Exception {
        UUID userId = UUID.fromString(USER_ID);
        UUID notificationId = UUID.randomUUID();
        willThrow(new SecurityException("Not authorized to modify this notification"))
                .given(notificationService).markRead(notificationId, userId);

        mockMvc.perform(put("/notifications/{id}/read", notificationId)
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "USER"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("marking an unknown notification read is 404")
    void markReadOnUnknownNotificationIsNotFound() throws Exception {
        UUID userId = UUID.fromString(USER_ID);
        UUID notificationId = UUID.randomUUID();
        willThrow(new IllegalArgumentException("Notification not found: " + notificationId))
                .given(notificationService).markRead(notificationId, userId);

        mockMvc.perform(put("/notifications/{id}/read", notificationId)
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "USER"))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------------------ helpers

    private static Page<Notification> page(Notification... notifications) {
        List<Notification> content = List.of(notifications);
        return new PageImpl<>(content, PageRequest.of(0, 20), content.size());
    }

    private static Notification notification(UUID id, UUID userId, boolean read) {
        return Notification.builder()
                .id(id)
                .userId(userId)
                .type("BOOKING_CONFIRMED")
                .title("Booking confirmed")
                .body("Your session is booked.")
                .read(read)
                .createdAt(Instant.parse("2026-08-01T10:00:00Z"))
                .build();
    }

    /**
     * Which of the two an unauthenticated request yields is a Spring Security entry-point
     * detail; the property under test is that the request is refused.
     */
    private static org.hamcrest.Matcher<Integer> anyOf401Or403() {
        return org.hamcrest.Matchers.isOneOf(401, 403);
    }
}
