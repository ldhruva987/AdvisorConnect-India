import { useQuery } from '@tanstack/react-query'
import apiClient from '@/lib/axios'
import type { AdminStats } from '@/types'
import type { AdminStatsDto } from '@/types/api'

export const adminStatsQueryKey = () => ['admin', 'stats'] as const

/**
 * Widen today's partial `/admin/stats` response into the dashboard's full
 * `AdminStats` shape.
 *
 * The endpoint is real but currently returns only `{ approvedAdvisors,
 * totalAdminActions }` — two counts derived from the audit log — while the
 * dashboard needs four figures plus their deltas. Backend Phase 10 adds Kafka-
 * fed counters for the rest.
 *
 * Rather than have the page render `undefined`, missing figures default to
 * `0` and missing deltas to `''` (the UI renders no delta chip for an empty
 * string, which is honest: we don't know the trend yet, so we claim nothing).
 * `approvedAdvisors` is used as the stand-in for `activeAdvisors` until the
 * real counter exists — it is the closest true statement available, and it
 * yields to the real field the moment the backend sends one.
 */
export function toAdminStats(dto: AdminStatsDto): AdminStats {
  return {
    pendingApplications: dto.pendingApplications ?? 0,
    activeAdvisors: dto.activeAdvisors ?? dto.approvedAdvisors ?? 0,
    totalUsers: dto.totalUsers ?? 0,
    platformRevenue: dto.platformRevenue ?? 0,
    pendingApplicationsDelta: dto.pendingApplicationsDelta ?? '',
    activeAdvisorsDelta: dto.activeAdvisorsDelta ?? '',
    totalUsersDelta: dto.totalUsersDelta ?? '',
    platformRevenueDelta: dto.platformRevenueDelta ?? '',
  }
}

/** `GET /admin/stats`. Real endpoint, partial response — see `toAdminStats`. */
export async function fetchAdminStats(): Promise<AdminStats> {
  const { data } = await apiClient.get<AdminStatsDto>('/admin/stats')
  return toAdminStats(data ?? {})
}

/** Platform figures for AdminDashboardPage's stat tiles. */
export function useAdminStats() {
  return useQuery({
    queryKey: adminStatsQueryKey(),
    queryFn: fetchAdminStats,
  })
}
