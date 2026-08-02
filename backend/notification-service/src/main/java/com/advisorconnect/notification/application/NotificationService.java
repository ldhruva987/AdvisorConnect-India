package com.advisorconnect.notification.application;

import com.advisorconnect.notification.domain.model.Notification;
import com.advisorconnect.notification.domain.port.out.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * The notifications inbox: the durable half of what this service does.
 *
 * <p>Email is best-effort and unverifiable from inside the system — SMTP may be unconfigured,
 * the provider may bounce, the user may never open it. A persisted notification is neither, so
 * every event that produces an email produces one of these first.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public Notification create(UUID userId, String type, String title, String body) {
        Notification saved = notificationRepository.save(Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .body(body)
                .read(false)
                .createdAt(Instant.now())
                .build());
        log.info("Notification persisted: id={} userId={} type={}", saved.getId(), userId, type);
        return saved;
    }

    /** The caller's own inbox, newest first. Scoped by construction — no id is accepted. */
    @Transactional(readOnly = true)
    public Page<Notification> listForUser(UUID userId, Pageable pageable) {
        return notificationRepository.findByUserId(userId, pageable);
    }

    /**
     * Marks one notification read.
     *
     * <p>Idempotent: marking an already-read notification is a no-op rather than an error, since
     * a client retrying a request must not be punished for it.
     *
     * @throws IllegalArgumentException if no such notification exists
     * @throws SecurityException        if it belongs to somebody else. Same exception type
     *                                  booking-service throws for its ownership checks, so the
     *                                  two services fail identically; the local
     *                                  {@code GlobalExceptionHandler} renders it as 403 rather
     *                                  than letting it surface as a 500.
     */
    public Notification markRead(UUID id, UUID userId) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + id));

        if (!notification.getUserId().equals(userId)) {
            // Deliberately not silently ignored: a mismatch means the caller guessed or was
            // handed an id that is not theirs, which is worth surfacing and logging.
            log.warn("Rejected mark-read of notification id={} by non-owner userId={}", id, userId);
            throw new SecurityException("Not authorized to modify this notification");
        }

        if (notification.isRead()) {
            return notification;
        }
        notification.setRead(true);
        return notificationRepository.save(notification);
    }
}
