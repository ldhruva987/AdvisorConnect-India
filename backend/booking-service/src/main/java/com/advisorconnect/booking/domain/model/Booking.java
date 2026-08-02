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

    /**
     * Exclusive end of the session, i.e. {@code sessionDateTime + durationMinutes}.
     *
     * <p>Denormalised on purpose. Availability has to ask "does this candidate slot overlap
     * any existing booking?", and answering that from the start time alone meant a 60-minute
     * booking only ever blocked its first 30-minute slot — the second half stayed bookable.
     *
     * <p>Nullable rather than {@code nullable = false}: the column is additive on an existing
     * table, and rows written before this field existed have no end time. Readers fall back to
     * {@code sessionDateTime + durationMinutes} when it is null.
     */
    private Instant sessionEndDateTime;

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
