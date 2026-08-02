package com.advisorconnect.admin.application;

import com.advisorconnect.admin.domain.model.PlatformCounters;
import com.advisorconnect.admin.domain.port.out.PlatformCountersRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Assembles the admin dashboard figures.
 *
 * <p>Four of the five come from the event-derived counters; {@code totalAdminActions} is a live
 * count of the audit trail, which admin-service does own, so there is nothing to accumulate.
 *
 * <p>{@code approvedAdvisors} now reads the counter rather than counting audit rows with
 * {@code action = 'ADVISOR_APPROVED'} as it used to. Counting audit rows conflated "approvals that
 * happened" with "times somebody posted an audit entry saying so", and double-counted an approval
 * that both the consumer and the admin UI recorded.
 */
@Service
@RequiredArgsConstructor
public class StatsService {

    private static final int CENTS_SCALE = 2;

    private final PlatformCountersRepository counters;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public PlatformStats currentStats() {
        PlatformCounters c = counters.load();
        return new PlatformStats(
                c.getApprovedAdvisors(),
                auditLogService.countAll(),
                c.getPendingApplications(),
                c.getTotalUsers(),
                // valueOf(unscaled, scale) — an exact cents-to-dollars move with no division and
                // no binary floating point anywhere near the money.
                BigDecimal.valueOf(c.getPlatformRevenueCents(), CENTS_SCALE));
    }
}
