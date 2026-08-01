package com.advisorconnect.booking.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private UUID advisorId;

    @Column(nullable = false)
    private Instant sessionDateTime;

    @Column(nullable = false)
    private int durationMinutes; // 30 or 60

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amountCharged;

    private String stripePaymentIntentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status = BookingStatus.PENDING;

    private Instant createdAt = Instant.now();
    private Instant completedAt;
}
