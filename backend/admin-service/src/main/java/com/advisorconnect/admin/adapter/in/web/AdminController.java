package com.advisorconnect.admin.adapter.in.web;

import com.advisorconnect.admin.application.AuditLogService;
import com.advisorconnect.admin.application.PlatformStats;
import com.advisorconnect.admin.application.StatsService;
import com.advisorconnect.admin.domain.model.AuditLog;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Admin-only endpoints. {@code SecurityConfig} requires the ADMIN role on {@code /admin/**},
 * reconstructed from the {@code X-User-*} headers the API Gateway injects.
 *
 * <p>The controller used to hold an {@code EntityManager} and write JPQL inline — the only place
 * in the codebase where the web layer talked to the database directly. Persistence now sits
 * behind services and ports like everywhere else; the endpoint contracts are unchanged.
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AuditLogService auditLogService;
    private final StatsService statsService;

    /** GET /admin/audit-logs — paginated audit trail, newest first */
    @GetMapping("/audit-logs")
    public ResponseEntity<List<AuditLog>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(auditLogService.listRecent(page, size));
    }

    /** POST /admin/audit-logs — record an admin action */
    @PostMapping("/audit-logs")
    public ResponseEntity<AuditLog> createAuditLog(
            @RequestHeader("X-User-Id") UUID adminId,
            @RequestBody AuditLogRequest req) {
        AuditLog log = auditLogService.record(
                adminId, req.action(), req.targetId(), req.targetType(), req.note());
        return ResponseEntity.ok(log);
    }

    /**
     * GET /admin/stats — platform statistics.
     *
     * <p>{@code approvedAdvisors} and {@code totalAdminActions} keep their existing names; the
     * pending-application, user and revenue figures are additive.
     */
    @GetMapping("/stats")
    public ResponseEntity<PlatformStats> getStats() {
        return ResponseEntity.ok(statsService.currentStats());
    }

    record AuditLogRequest(String action, String targetId, String targetType, String note) {}
}
