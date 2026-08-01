package com.advisorconnect.admin.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
public class AuditLog {

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
