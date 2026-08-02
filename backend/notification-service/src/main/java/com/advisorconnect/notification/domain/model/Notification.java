package com.advisorconnect.notification.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A notification the user can read back later.
 *
 * <p>notification-service used to be write-only and fire-and-forget: an event arrived, an email
 * was attempted, and if SMTP was unconfigured or the send failed the fact that anything had
 * happened was lost entirely — there was no entity, no table and no way to ask "what happened
 * to my account?". Persisting first makes email a best-effort delivery channel over a record
 * that survives it.
 *
 * <p>{@code read} is mapped to {@code is_read}: {@code read} is a reserved word in several SQL
 * dialects and an unquoted column of that name is a portability trap.
 */
@Entity
@Table(
        name = "notifications",
        indexes = @Index(name = "idx_notifications_user_created", columnList = "userId, createdAt")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Recipient. The only axis the inbox is ever queried on, hence the composite index. */
    @Column(nullable = false)
    private UUID userId;

    /**
     * Machine-readable discriminator (e.g. {@code BOOKING_CONFIRMED}), for the client to pick an
     * icon or a deep link. A plain String rather than an enum so that a new event type published
     * by another service does not fail to deserialise on rows already written.
     */
    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 2000)
    private String body;

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private boolean read = false;

    @Column(nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
