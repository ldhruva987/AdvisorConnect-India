import { useQuery } from '@tanstack/react-query'
import apiClient from '@/lib/axios'
import type { AdvisorReview } from '@/types'
import type { AdvisorReviewDto, Page } from '@/types/api'

export interface AdvisorReviewsPage {
  reviews: AdvisorReview[]
  totalElements: number
  totalPages: number
}

export const advisorReviewsQueryKey = (advisorId: string | undefined, page = 0) =>
  ['advisor-reviews', advisorId, page] as const

/**
 * `GET /advisors/{id}/reviews` — pending backend Phase 9.
 *
 * Modelled as a Spring `Page<AdvisorReviewDto>` rather than a bare array: the
 * contract calls this endpoint paginated, and every other paginated endpoint
 * in this backend returns the same `Page` envelope. `AdvisorReviewDto` and the
 * UI's `AdvisorReview` are field-for-field identical, so there is no mapper
 * here — inventing one would be pure indirection.
 */
export async function fetchAdvisorReviews(
  advisorId: string,
  page: number,
): Promise<AdvisorReviewsPage> {
  const { data } = await apiClient.get<Page<AdvisorReviewDto>>(
    `/advisors/${encodeURIComponent(advisorId)}/reviews`,
    { params: { page } },
  )

  return {
    reviews: data.content ?? [],
    totalElements: data.totalElements ?? 0,
    totalPages: data.totalPages ?? 0,
  }
}

/** Paged reviews for one advisor. Skipped until the advisor id is known. */
export function useAdvisorReviews(advisorId: string | undefined, page = 0) {
  return useQuery({
    queryKey: advisorReviewsQueryKey(advisorId, page),
    queryFn: () => fetchAdvisorReviews(advisorId!, page),
    enabled: !!advisorId,
  })
}
