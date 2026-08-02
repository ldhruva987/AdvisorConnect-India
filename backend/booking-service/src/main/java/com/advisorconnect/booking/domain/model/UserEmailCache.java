package com.advisorconnect.booking.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * A local projection of {@code userId -> email}, populated from the {@code user.registered}
 * topic that auth-service publishes.
 *
 * <p>Exists because {@code booking.created}, {@code booking.cancelled} and
 * {@code booking.completed} have to carry the client's email — notification-service reads
 * {@code userEmail} off the event and silently sends nothing when it is absent, which is
 * exactly what used to happen: no booking email was ever delivered. booking-service does not
 * own the email field, and the alternatives were a synchronous call into auth-service on every
 * booking write (coupling the booking path to another service's availability) or leaving the
 * field out. A cache fed by the event stream keeps the booking transaction local.
 *
 * <p>Deliberately a second, independent copy of the same read model advisor-service keeps:
 * these are separate bounded contexts with separate databases, so the table is duplicated
 * rather than shared. Same shape and same name on both sides, no code shared across services.
 *
 * <p>{@code id} is the auth-service user id, assigned rather than generated: the whole point is
 * that both services agree on it.
 */
@Entity
@Table(name = "user_email_cache")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEmailCache {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String email;
}
