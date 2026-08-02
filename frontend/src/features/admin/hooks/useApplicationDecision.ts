import { useMutation, useQueryClient, type UseMutationResult } from '@tanstack/react-query'
import apiClient from '@/lib/axios'
import { adminStatsQueryKey } from './useAdminStats'
import type { AdminDecisionRequest } from '@/types/api'

export type ApplicationDecision = 'approve' | 'reject'

export interface ApplicationDecisionVariables {
  id: string
  decision: ApplicationDecision
  /** Admin's rationale. Sent as `''` rather than omitted — the backend's
   *  `AdminDecisionRequest` always reads a `notes` field. */
  notes?: string
}

/** `PUT /advisors/applications/{id}/approve|reject`. REAL backend endpoints. */
export async function putApplicationDecision({
  id,
  decision,
  notes,
}: ApplicationDecisionVariables): Promise<void> {
  const body: AdminDecisionRequest = { notes: notes ?? '' }
  await apiClient.put(`/advisors/applications/${encodeURIComponent(id)}/${decision}`, body)
}

/**
 * Approve or reject an advisor application.
 *
 * One mutation covering both verbs rather than two hooks: the two buttons sit
 * side by side, submit the same notes field, and invalidate the same caches.
 * Splitting them would duplicate all of that for a one-word difference in the
 * URL.
 *
 * Both endpoints return an empty body, so there is nothing to hand back — the
 * updated record comes from the invalidated list refetch. Stats are
 * invalidated too: approving moves an application out of `pendingApplications`
 * and into the advisor count.
 *
 * These are wired against the real backend, not a placeholder. Today the
 * Approve/Reject buttons on AdminDashboardPage have no `onClick` at all.
 */
export function useApplicationDecision(): UseMutationResult<
  void,
  Error,
  ApplicationDecisionVariables
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: putApplicationDecision,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['applications'] })
      void queryClient.invalidateQueries({ queryKey: adminStatsQueryKey() })
    },
  })
}
