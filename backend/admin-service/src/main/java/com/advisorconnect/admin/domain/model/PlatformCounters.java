package com.advisorconnect.admin.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Running totals behind {@code GET /admin/stats}, maintained by the Kafka consumers.
 *
 * <p>A single row with a fixed id. The dashboard figures cannot be derived by counting rows in
 * this database — admin-service owns no users, applications or bookings — and asking four other
 * services for a count on every dashboard load would make the page as slow and as fragile as the
 * slowest of them. So the numbers are accumulated from the events those services already publish.
 *
 * <p>Writes go through atomic {@code UPDATE ... SET x = x + :delta} statements rather than
 * read-modify-write: several Kafka listener threads increment concurrently, and load-then-save
 * would silently lose increments whenever two events land in the same instant.
 *
 * <p>Because the totals are event-derived they are a projection, not a ledger: a redelivered
 * Kafka message is counted twice, and events published while this service was down are never
 * counted at all. Both are acceptable for a dashboard and neither is acceptable for billing.
 */
@Entity
@Table(name = "platform_counters")
public class PlatformCounters {

    /** The only row that ever exists. */
    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;

    @Column(nullable = false)
    private long totalUsers;

    @Column(nullable = false)
    private long pendingApplications;

    @Column(nullable = false)
    private long approvedAdvisors;

    /** Completed-session revenue in cents. Integer cents, never a float — see BookingEventConsumer. */
    @Column(nullable = false)
    private long platformRevenueCents;

    // ─── Getters & Setters ───────────────────────────────────────────────────
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public long getTotalUsers() { return totalUsers; }
    public void setTotalUsers(long totalUsers) { this.totalUsers = totalUsers; }
    public long getPendingApplications() { return pendingApplications; }
    public void setPendingApplications(long pendingApplications) { this.pendingApplications = pendingApplications; }
    public long getApprovedAdvisors() { return approvedAdvisors; }
    public void setApprovedAdvisors(long approvedAdvisors) { this.approvedAdvisors = approvedAdvisors; }
    public long getPlatformRevenueCents() { return platformRevenueCents; }
    public void setPlatformRevenueCents(long platformRevenueCents) { this.platformRevenueCents = platformRevenueCents; }
}
