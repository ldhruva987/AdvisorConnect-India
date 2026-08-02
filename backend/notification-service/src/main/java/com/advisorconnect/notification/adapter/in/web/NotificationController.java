package com.advisorconnect.notification.adapter.in.web;

import com.advisorconnect.notification.adapter.in.web.dto.NotificationDto;
import com.advisorconnect.notification.application.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The notifications inbox. notification-service previously exposed no HTTP surface at all —
 * it consumed Kafka and sent mail, and a user had no way to see what the platform had told them.
 */
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * The caller's own notifications, newest first.
     *
     * <p>The recipient comes from the gateway-injected {@code X-User-Id} header, never from a
     * path or query parameter, so there is no shape of this request that reads somebody else's
     * inbox.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Page<NotificationDto> list(
            @RequestHeader("X-User-Id") UUID userId,
            @PageableDefault(size = 20) Pageable pageable) {
        return notificationService.listForUser(userId, pageable).map(NotificationDto::from);
    }

    /** Marks one of the caller's notifications read. Ownership is enforced in the service. */
    @PutMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public NotificationDto markRead(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID userId) {
        return NotificationDto.from(notificationService.markRead(id, userId));
    }
}
