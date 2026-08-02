package com.advisorconnect.admin.application;

import com.advisorconnect.admin.domain.model.AuditLog;
import com.advisorconnect.admin.domain.port.out.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * The audit trail, as a service rather than as JPQL embedded in the controller.
 *
 * <p>Two callers now write to it: the admin UI via {@code POST /admin/audit-logs}, and the Kafka
 * consumers, which record advisor lifecycle decisions as they happen instead of relying on the
 * UI to remember to post one.
 */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    /** Guards against a caller asking for the whole table in one page. */
    private static final int MAX_PAGE_SIZE = 100;

    private final AuditLogRepository repository;

    @Transactional(readOnly = true)
    public List<AuditLog> listRecent(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return repository.findRecent(
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @Transactional
    public AuditLog record(UUID adminId, String action, String targetId, String targetType, String note) {
        AuditLog log = new AuditLog();
        log.setAdminId(adminId != null ? adminId : AuditLog.SYSTEM_ACTOR);
        log.setAction(action);
        log.setTargetId(targetId);
        log.setTargetType(targetType);
        log.setNote(note);
        return repository.save(log);
    }

    /** Total entries ever recorded — the {@code totalAdminActions} figure on the dashboard. */
    @Transactional(readOnly = true)
    public long countAll() {
        return repository.count();
    }
}
