import { keepPreviousData, useQuery } from '@tanstack/react-query'
import apiClient from '@/lib/axios'
import { mapApplicationDto } from '@/lib/mapApplication'
import type { AdvisorApplication, ApplicationStatus } from '@/types'
import type { AdvisorApplicationSummaryDto, Page } from '@/types/api'

export interface ApplicationsPage {
  applications: AdvisorApplication[]
  totalElements: number
  totalPages: number
}

/**
 * Query key for the application list.
 *
 * Exported because `useApplicationDetail` reads this exact cache entry — see
 * the note there. `status ?? null` rather than leaving it `undefined` so the
 * key serialises stably: TanStack hashes keys, and `undefined` members are
 * dropped, which would make `['applications', undefined]` collide with
 * `['applications']`.
 */
export const applicationsQueryKey = (status?: ApplicationStatus) =>
  ['applications', status ?? null] as const

/** `GET /advisors/applications?status=` — pending backend Phase 5 (admin-only). */
export async function fetchApplications(
  status: ApplicationStatus | undefined,
  page: number,
): Promise<ApplicationsPage> {
  const { data } = await apiClient.get<Page<AdvisorApplicationSummaryDto>>(
    '/advisors/applications',
    { params: { status, page } },
  )

  return {
    // Mapped here rather than in `select` so the cache holds UI-shaped
    // applications — `useApplicationDetail` reads straight out of it.
    applications: (data.content ?? []).map(mapApplicationDto),
    totalElements: data.totalElements ?? 0,
    totalPages: data.totalPages ?? 0,
  }
}

/** The admin review queue. Backs AdminDashboardPage's applications table. */
export function useApplications(status?: ApplicationStatus, page = 0) {
  return useQuery({
    queryKey: [...applicationsQueryKey(status), page],
    queryFn: () => fetchApplications(status, page),
    placeholderData: keepPreviousData,
  })
}
