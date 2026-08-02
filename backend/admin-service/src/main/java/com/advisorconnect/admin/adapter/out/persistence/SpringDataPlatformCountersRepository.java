package com.advisorconnect.admin.adapter.out.persistence;

import com.advisorconnect.admin.domain.model.PlatformCounters;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Each mutation is a single UPDATE evaluated by the database, so concurrent listener threads
 * serialise on the row rather than racing through a read-modify-write cycle in the JVM.
 *
 * <p>{@code clearAutomatically} matters: without it a {@code PlatformCounters} already loaded in
 * the persistence context would keep serving pre-update values to the same transaction.
 *
 * <p>Each method returns the affected row count so the adapter can tell "incremented" from
 * "the singleton row does not exist yet".
 */
interface SpringDataPlatformCountersRepository extends JpaRepository<PlatformCounters, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PlatformCounters p SET p.totalUsers = p.totalUsers + :delta WHERE p.id = 1")
    int addTotalUsers(@Param("delta") long delta);

    /**
     * Clamped at zero inside the statement, which keeps the floor atomic — reading the value,
     * deciding, and then writing would let a concurrent decrement slip underneath.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PlatformCounters p SET p.pendingApplications = "
            + "CASE WHEN p.pendingApplications + :delta < 0 THEN 0 ELSE p.pendingApplications + :delta END "
            + "WHERE p.id = 1")
    int addPendingApplications(@Param("delta") long delta);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PlatformCounters p SET p.approvedAdvisors = p.approvedAdvisors + :delta WHERE p.id = 1")
    int addApprovedAdvisors(@Param("delta") long delta);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PlatformCounters p SET p.platformRevenueCents = p.platformRevenueCents + :delta WHERE p.id = 1")
    int addPlatformRevenueCents(@Param("delta") long delta);
}
