package com.advisorconnect.notification.adapter.out.persistence;

import com.advisorconnect.notification.domain.model.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface SpringDataNotificationRepository extends JpaRepository<Notification, UUID> {

    /**
     * Newest first is baked into the query rather than left to the caller's {@code Pageable}:
     * an inbox with an unspecified order is a paging bug waiting to happen, because Postgres is
     * free to return rows in a different order on every page request.
     */
    Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
