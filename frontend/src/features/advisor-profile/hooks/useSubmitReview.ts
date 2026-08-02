import { useMutation, useQueryClient, type UseMutationResult } from '@tanstack/react-query'
import apiClient from '@/lib/axios'
import type { AdvisorReview } from '@/types'
import type { SubmitReviewRequest } from '@/types/api'

/** `POST /advisors/{id}/reviews` — pending backend Phase 9. */
export async function postReview(
  advisorId: string,
  body: SubmitReviewRequest,
): Promise<AdvisorReview> {
  const { data } = await apiClient.post<AdvisorReview>(
    `/advisors/${encodeURIComponent(advisorId)}/reviews`,
    body,
  )
  return data
}

/**
 * Leave a review for an advisor.
 *
 * The backend gates this on a completed, not-yet-reviewed booking between the
 * two parties, which is why `bookingId` is part of the body rather than
 * something the server infers — a user with two completed sessions gets to
 * review each one.
 *
 * On success both the review list and the advisor profile are invalidated:
 * the profile carries `averageRating`/`reviewCount`, which the backend
 * recomputes in the same transaction, so leaving it cached would show a stale
 * rating right next to the review that just changed it.
 */
export function useSubmitReview(
  advisorId: string | undefined,
): UseMutationResult<AdvisorReview, Error, SubmitReviewRequest> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (body: SubmitReviewRequest) => postReview(advisorId!, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['advisor-reviews', advisorId] })
      void queryClient.invalidateQueries({ queryKey: ['advisor'] })
    },
  })
}
