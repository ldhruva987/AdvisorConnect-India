package com.advisorconnect.admin.application;

import java.math.BigDecimal;

/**
 * The admin dashboard figures.
 *
 * <p>{@code approvedAdvisors} and {@code totalAdminActions} keep the names and meanings they had
 * when this was a two-field record nested in the controller, so existing clients are unaffected.
 *
 * @param platformRevenue completed-session revenue in currency units (dollars), not cents. The
 *                        counter is stored in cents to keep accumulation exact; the conversion to
 *                        a scale-2 {@link BigDecimal} happens once, on the way out.
 */
public record PlatformStats(
        long approvedAdvisors,
        long totalAdminActions,
        long pendingApplications,
        long totalUsers,
        BigDecimal platformRevenue) {
}
