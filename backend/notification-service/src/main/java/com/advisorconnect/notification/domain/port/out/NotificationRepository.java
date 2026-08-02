package com.advisorconnect.notification.domain.port.out;

import com.advisorconnect.notification.domain.model.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository {

    Notification save(Notification notification);

    Optional<Notification> findById(UUID id);

    /** One user's inbox, newest first. Scoped by construction — no unscoped listing exists. */
    Page<Notification> findByUserId(UUID userId, Pageable pageable);
}
