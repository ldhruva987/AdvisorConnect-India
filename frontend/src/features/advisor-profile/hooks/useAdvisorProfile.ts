import { useQuery } from '@tanstack/react-query'
import apiClient from '@/lib/axios'
import { mapAdvisorDto } from '@/lib/mapAdvisor'
import type { AdvisorPublic } from '@/types'
import type { AdvisorPublicDto } from '@/types/api'

export const advisorProfileQueryKey = (username: string | undefined) =>
  ['advisor', username] as const

/** `GET /advisors/{username}`. Real backend endpoint. */
export async function fetchAdvisorProfile(username: string): Promise<AdvisorPublic> {
  const { data } = await apiClient.get<AdvisorPublicDto>(
    `/advisors/${encodeURIComponent(username)}`,
  )
  return mapAdvisorDto(data)
}

/**
 * A single advisor's public profile, looked up by the `:username` route param.
 *
 * `enabled: !!username` matters here beyond the usual tidiness: AdvisorProfilePage
 * currently doesn't call `useParams()` at all, and once it does, the first
 * render of a direct navigation can legitimately see `undefined`. Without the
 * guard that becomes a request to `/advisors/undefined`.
 */
export function useAdvisorProfile(username: string | undefined) {
  return useQuery({
    queryKey: advisorProfileQueryKey(username),
    queryFn: () => fetchAdvisorProfile(username!),
    enabled: !!username,
  })
}
