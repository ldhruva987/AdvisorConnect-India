package com.advisorconnect.advisor.domain.model;

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
 * <p>Exists because {@code advisor.approved} and {@code advisor.rejected} need to carry the
 * advisor's email — notification-service has to know where to send the decision — and
 * advisor-service does not own that field. The alternatives were a synchronous call into
 * auth-service on every approval (coupling a write path to another service's availability) or
 * leaving the email out (which is what happened before, and is why approval emails were never
 * sent). A cache fed by the event stream keeps the approval transaction local.
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
