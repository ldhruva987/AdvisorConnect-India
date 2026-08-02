package com.advisorconnect.admin.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
public class AuditLog {

    /**
     * Actor recorded for entries the platform writes about itself rather than about something a
     * human administrator did — currently {@code ADVISOR_APPLICATION_SUBMITTED}, which is caused
     * by the applicant, not by an admin.
     *
     * <p>A sentinel rather than {@code null} because {@code adminId} is {@code NOT NULL} and this
     * service runs {@code ddl-auto: validate} against an externally-managed schema; relaxing the
     * column would need a migration that does not exist in this repository.
     */
    public static final UUID SYSTEM_ACTOR = new UUID(0L, 0L);

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false)
    private UUID adminId;

    @Column(nullable = false)
    private String action;         // e.g. "ADVISOR_APPROVED", "ADVISOR_REJECTED"

    private String targetId;       // entity acted upon
    private String targetType;     // e.g. "AdvisorApplication"
    private String note;

    private Instant createdAt;

    @PrePersist
    void prePersist() { createdAt = Instant.now(); }

    // ─── Getters & Setters ───────────────────────────────────────────────────
    public UUID getId() { return id; }
    public UUID getAdminId() { return adminId; }
    public void setAdminId(UUID adminId) { this.adminId = adminId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Instant getCreatedAt() { return createdAt; }
}
