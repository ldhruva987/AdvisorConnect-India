import { useQueryClient } from '@tanstack/react-query'
import { applicationsQueryKey, type ApplicationsPage } from './useApplications'
import type { AdvisorApplication, ApplicationStatus } from '@/types'

export interface UseApplicationDetailResult {
  application: AdvisorApplication | null
  /**
   * `false` when the list hasn't been fetched yet (or this id isn't on any
   * loaded page), so the caller can distinguish "still loading the list" from
   * "no such application".
   */
  isAvailable: boolean
}

/**
 * A single application, for the admin detail panel.
 *
 * STOPGAP — there is no `GET /advisors/applications/{id}` endpoint. Rather
 * than invent one the backend doesn't serve, this reads the record out of
 * whatever `useApplications` has already cached. That is sufficient because
 * the detail panel is only ever opened from a row in that same list, so the
 * data is guaranteed to be in the cache by the time it renders.
 *
 * It also fixes the bug this replaces: AdminDashboardPage currently shows
 * `MOCK_APPLICATIONS[0]` no matter which row was clicked. Looking the record
 * up *by id* is the whole point.
 *
 * Uses `getQueriesData` (prefix match) rather than `getQueryData` (exact
 * match) so the lookup spans every cached page of the list, not just page 0 —
 * `useApplications` includes the page number in its key.
 *
 * When a real single-application endpoint ships, this becomes an ordinary
 * `useQuery` and the signature stays the same.
 */
export function useApplicationDetail(
  id: string | undefined,
  status?: ApplicationStatus,
): UseApplicationDetailResult {
  const queryClient = useQueryClient()

  const cachedPages = queryClient.getQueriesData<ApplicationsPage>({
    queryKey: applicationsQueryKey(status),
  })

  if (!id) return { application: null, isAvailable: false }

  for (const [, page] of cachedPages) {
    const match = page?.applications.find((application) => application.id === id)
    if (match) return { application: match, isAvailable: true }
  }

  return { application: null, isAvailable: false }
}
