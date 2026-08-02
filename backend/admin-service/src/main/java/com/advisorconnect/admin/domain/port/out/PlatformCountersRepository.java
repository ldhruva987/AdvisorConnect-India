package com.advisorconnect.admin.domain.port.out;

import com.advisorconnect.admin.domain.model.PlatformCounters;

/**
 * Outbound port for the dashboard counters.
 *
 * <p>Every mutation is expressed as a delta rather than as "save this object", so the adapter can
 * implement it as one atomic UPDATE. Handing out the entity for callers to mutate would reopen
 * the lost-update race the deltas exist to avoid.
 */
public interface PlatformCountersRepository {

    /** Current totals; zeroes if no event has ever been consumed. */
    PlatformCounters load();

    void addTotalUsers(long delta);

    /**
     * Floors at zero. A negative "pending applications" is meaningless on a dashboard, and the
     * count can legitimately go under: an {@code advisor.approved} for an application submitted
     * before this service first started has no matching increment to cancel out.
     */
    void addPendingApplications(long delta);

    void addApprovedAdvisors(long delta);

    void addPlatformRevenueCents(long deltaCents);
}
