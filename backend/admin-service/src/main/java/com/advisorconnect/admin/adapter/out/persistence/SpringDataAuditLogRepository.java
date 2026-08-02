package com.advisorconnect.admin.adapter.out.persistence;

import com.advisorconnect.admin.domain.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface SpringDataAuditLogRepository extends JpaRepository<AuditLog, UUID> {
}
