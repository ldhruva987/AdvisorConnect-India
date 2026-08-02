package com.advisorconnect.notification.adapter.in.web.dto;

import com.advisorconnect.notification.domain.model.Notification;

import java.time.Instant;
import java.util.UUID;

/**
 * Wire shape of a notification.
 *
 * <p>{@code userId} is deliberately absent: every response is already scoped to the
 * authenticated caller, so echoing their own id back adds nothing and only widens what an
 * accidentally-cached response leaks.
 */
public record NotificationDto(
        UUID id,
        String type,
        String title,
        String body,
        boolean read,
        Instant createdAt) {

    public static NotificationDto from(Notification n) {
        return new NotificationDto(
                n.getId(), n.getType(), n.getTitle(), n.getBody(), n.isRead(), n.getCreatedAt());
    }
}
