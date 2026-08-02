package com.advisorconnect.admin.application;

import com.advisorconnect.admin.domain.model.PlatformCounters;
import com.advisorconnect.admin.domain.port.out.PlatformCountersRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    @Mock
    private PlatformCountersRepository counters;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private StatsService statsService;

    @Test
    @DisplayName("every counter reaches the dashboard under the right name")
    void mapsCountersOntoTheDashboardFigures() {
        given(counters.load()).willReturn(countersOf(1_204, 7, 63, 45_000));
        given(auditLogService.countAll()).willReturn(312L);

        PlatformStats stats = statsService.currentStats();

        assertThat(stats.totalUsers()).isEqualTo(1_204);
        assertThat(stats.pendingApplications()).isEqualTo(7);
        assertThat(stats.approvedAdvisors()).isEqualTo(63);
        assertThat(stats.totalAdminActions()).isEqualTo(312);
        assertThat(stats.platformRevenue()).isEqualByComparingTo(new BigDecimal("450.00"));
    }

    @Test
    @DisplayName("approvedAdvisors comes from the counter, not from counting audit rows")
    void approvedAdvisorsIgnoresTheAuditRowCount() {
        // The old implementation counted audit rows with action = 'ADVISOR_APPROVED', so an
        // approval recorded by both the consumer and the admin UI was counted twice.
        given(counters.load()).willReturn(countersOf(0, 0, 2, 0));
        given(auditLogService.countAll()).willReturn(99L);

        assertThat(statsService.currentStats().approvedAdvisors()).isEqualTo(2);
    }

    @Test
    @DisplayName("revenue is reported in whole currency units, exactly")
    void convertsCentsToCurrencyUnitsWithoutLosingCents() {
        given(counters.load()).willReturn(countersOf(0, 0, 0, 9_001));

        // 9001 cents is $90.01 — not $90.00, and not 90.00999999999999 either.
        assertThat(statsService.currentStats().platformRevenue())
                .isEqualByComparingTo(new BigDecimal("90.01"));
    }

    @Test
    @DisplayName("a single cent is not rounded away")
    void oneCentSurvives() {
        given(counters.load()).willReturn(countersOf(0, 0, 0, 1));

        assertThat(statsService.currentStats().platformRevenue())
                .isEqualByComparingTo(new BigDecimal("0.01"));
    }

    @Test
    @DisplayName("a brand new platform reports zeroes rather than failing")
    void reportsZeroesBeforeAnyEventHasBeenConsumed() {
        // load() hands back a default instance when the singleton row does not exist yet.
        given(counters.load()).willReturn(new PlatformCounters());
        given(auditLogService.countAll()).willReturn(0L);

        PlatformStats stats = statsService.currentStats();

        assertThat(stats.totalUsers()).isZero();
        assertThat(stats.pendingApplications()).isZero();
        assertThat(stats.approvedAdvisors()).isZero();
        assertThat(stats.totalAdminActions()).isZero();
        assertThat(stats.platformRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private static PlatformCounters countersOf(long users, long pending, long approved, long revenueCents) {
        PlatformCounters c = new PlatformCounters();
        c.setTotalUsers(users);
        c.setPendingApplications(pending);
        c.setApprovedAdvisors(approved);
        c.setPlatformRevenueCents(revenueCents);
        return c;
    }
}
