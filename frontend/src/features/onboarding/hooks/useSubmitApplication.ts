import { useMutation, useQueryClient, type UseMutationResult } from '@tanstack/react-query'
import apiClient from '@/lib/axios'
import type { SubmitApplicationRequest } from '@/types/api'

/**
 * `POST /advisors/apply`. REAL backend endpoint.
 *
 * Responds `201` with the new application's UUID as a bare JSON string — not
 * an object, and not the created record.
 */
export async function postApplication(body: SubmitApplicationRequest): Promise<string> {
  const { data } = await apiClient.post<string>('/advisors/apply', body)
  return data
}

/**
 * Submit an advisor onboarding application.
 *
 * `documentS3Keys` must already be populated by `usePresignedUpload` — the
 * backend requires a non-empty list and only stores keys, never file bytes.
 *
 * Invalidates the admin application list so an admin with the dashboard open
 * sees the new submission on their next refetch rather than a stale queue.
 */
export function useSubmitApplication(): UseMutationResult<string, Error, SubmitApplicationRequest> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: postApplication,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['applications'] })
    },
  })
}
