package com.advisorconnect.admin.adapter.in.web;

import com.advisorconnect.admin.domain.model.AuditLog;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Admin-only endpoints. The API Gateway enforces X-User-Role = ADMIN
 * before routing to this service.
 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    @PersistenceContext
    private EntityManager em;

    /** GET /admin/audit-logs — paginated audit trail */
    @GetMapping("/audit-logs")
    public ResponseEntity<List<AuditLog>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        TypedQuery<AuditLog> q = em.createQuery(
                "SELECT a FROM AuditLog a ORDER BY a.createdAt DESC", AuditLog.class);
        q.setFirstResult(page * size);
        q.setMaxResults(size);
        return ResponseEntity.ok(q.getResultList());
    }

    /** POST /admin/audit-logs — record an admin action */
    @PostMapping("/audit-logs")
    @Transactional
    public ResponseEntity<AuditLog> createAuditLog(
            @RequestHeader("X-User-Id") UUID adminId,
            @RequestBody AuditLogRequest req) {
        AuditLog log = new AuditLog();
        log.setAdminId(adminId);
        log.setAction(req.action());
        log.setTargetId(req.targetId());
        log.setTargetType(req.targetType());
        log.setNote(req.note());
        em.persist(log);
        return ResponseEntity.ok(log);
    }

    /** GET /admin/stats — platform statistics */
    @GetMapping("/stats")
    public ResponseEntity<PlatformStats> getStats() {
        long totalAdvisors = (long) em.createQuery(
                "SELECT COUNT(a) FROM AuditLog a WHERE a.action = 'ADVISOR_APPROVED'")
                .getSingleResult();
        long totalActions = (long) em.createQuery(
                "SELECT COUNT(a) FROM AuditLog a")
                .getSingleResult();
        return ResponseEntity.ok(new PlatformStats(totalAdvisors, totalActions));
    }

    record AuditLogRequest(String action, String targetId, String targetType, String note) {}
    record PlatformStats(long approvedAdvisors, long totalAdminActions) {}
}
