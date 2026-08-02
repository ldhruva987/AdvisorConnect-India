import { keepPreviousData, useQuery } from '@tanstack/react-query'
import apiClient from '@/lib/axios'
import { getErrorMessage } from '@/lib/getErrorMessage'
import { mapAdvisorDto } from '@/lib/mapAdvisor'
import type { AdvisorSectorEnum } from '@/lib/sectors'
import type { AdvisorPublic } from '@/types'
import type { AdvisorPublicDto, Page } from '@/types/api'

export interface UseAdvisorsParams {
  /** Backend enum value (`'MENTAL_HEALTH'`), not the display label. */
  sector?: AdvisorSectorEnum
  q?: string
  page?: number
}

export interface AdvisorsPage {
  advisors: AdvisorPublic[]
  totalElements: number
  totalPages: number
}

export const advisorsQueryKey = (params: UseAdvisorsParams) => ['advisors', params] as const

/**
 * `GET /advisors?sector=&q=&page=&size=`. Real backend endpoint.
 *
 * Undefined params are dropped by Axios rather than serialised as `"undefined"`,
 * so an unfiltered call is a clean `GET /advisors`.
 */
export async function fetchAdvisors({ sector, q, page }: UseAdvisorsParams): Promise<AdvisorsPage> {
  const { data } = await apiClient.get<Page<AdvisorPublicDto>>('/advisors', {
    params: { sector, q, page },
  })

  return {
    // Mapping in the queryFn (not in `select`) so the cache holds UI-shaped
    // advisors. Anything reading this key via `getQueryData` then sees the
    // same objects the hook returned, with no second mapping step.
    advisors: (data.content ?? []).map(mapAdvisorDto),
    totalElements: data.totalElements ?? 0,
    totalPages: data.totalPages ?? 0,
  }
}

/**
 * Paged advisor search, driving ExplorePage's list and LandingPage's featured
 * strip. Returns a flattened shape rather than the raw query object because
 * every caller wants the same four things and none of them should have to know
 * about Spring's `Page` envelope.
 *
 * `keepPreviousData` keeps the current page on screen while the next one
 * loads, so paging doesn't flash an empty list between renders.
 */
export function useAdvisors(params: UseAdvisorsParams = {}) {
  const query = useQuery({
    queryKey: advisorsQueryKey(params),
    queryFn: () => fetchAdvisors(params),
    placeholderData: keepPreviousData,
  })

  return {
    advisors: query.data?.advisors ?? [],
    totalElements: query.data?.totalElements ?? 0,
    totalPages: query.data?.totalPages ?? 0,
    isLoading: query.isLoading,
    isError: query.isError,
    error: query.error,
    /** Pre-formatted for `ErrorBanner`, so no page hand-rolls Axios copy. */
    errorMessage: query.error ? getErrorMessage(query.error) : null,
  }
}
