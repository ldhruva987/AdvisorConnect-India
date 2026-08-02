package com.advisorconnect.notification.adapter.out.persistence;

import com.advisorconnect.notification.domain.model.Notification;
import com.advisorconnect.notification.domain.port.out.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaNotificationRepository implements NotificationRepository {

    private final SpringDataNotificationRepository repo;

    @Override
    public Notification save(Notification notification) {
        return repo.save(notification);
    }

    @Override
    public Optional<Notification> findById(UUID id) {
        return repo.findById(id);
    }

    @Override
    public Page<Notification> findByUserId(UUID userId, Pageable pageable) {
        return repo.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }
}
