package com.advisorconnect.admin.domain.port.out;

import com.advisorconnect.admin.domain.model.AuditLog;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Outbound port for the audit trail.
 *
 * <p>Admin-service previously had no persistence abstraction at all — {@code AdminController}
 * held an {@code EntityManager} and wrote JPQL in the web layer, which is the only place in this
 * codebase that did so. This mirrors the port/adapter split every other service uses (see
 * advisor-service's {@code AdvisorProfileRepository}).
 */
public interface AuditLogRepository {

    AuditLog save(AuditLog log);

    /** Most recent first. */
    List<AuditLog> findRecent(Pageable pageable);

    long count();
}
