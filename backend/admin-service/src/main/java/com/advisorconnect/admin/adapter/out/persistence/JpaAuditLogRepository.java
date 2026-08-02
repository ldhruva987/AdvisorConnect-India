package com.advisorconnect.admin.adapter.out.persistence;

import com.advisorconnect.admin.domain.model.AuditLog;
import com.advisorconnect.admin.domain.port.out.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class JpaAuditLogRepository implements AuditLogRepository {

    private final SpringDataAuditLogRepository repo;

    @Override
    public AuditLog save(AuditLog log) {
        return repo.save(log);
    }

    /**
     * The caller supplies the sort (newest first), matching the {@code ORDER BY a.createdAt DESC}
     * the controller used to spell out inline.
     */
    @Override
    public List<AuditLog> findRecent(Pageable pageable) {
        return repo.findAll(pageable).getContent();
    }

    @Override
    public long count() {
        return repo.count();
    }
}
