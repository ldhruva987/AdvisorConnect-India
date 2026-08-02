import { useQuery } from '@tanstack/react-query'
import apiClient from '@/lib/axios'
import type { Booking } from '@/types'

export const myBookingsQueryKey = () => ['bookings', 'me'] as const

/** `GET /bookings/me` — pending backend Phase 3 (the repository method already exists). */
export async function fetchMyBookings(): Promise<Booking[]> {
  const { data } = await apiClient.get<Booking[]>('/bookings/me')
  return data ?? []
}

export interface UseMyBookingsOptions {
  /**
   * Defaults to `true`. Pass `false` on public pages so a signed-out visitor
   * doesn't fire an authenticated request that can only 401 — AdvisorProfilePage
   * uses this to decide whether to offer a review CTA.
   */
  enabled?: boolean
}

/**
 * The signed-in user's bookings. Backs the `/bookings` route, the review
 * eligibility check on AdvisorProfilePage, and AdvisorDashboardPage's Sessions
 * tab.
 *
 * No mapper: the contract for this endpoint is the UI's own `Booking` shape,
 * so there is nothing to translate.
 */
export function useMyBookings({ enabled = true }: UseMyBookingsOptions = {}) {
  return useQuery({
    queryKey: myBookingsQueryKey(),
    queryFn: fetchMyBookings,
    enabled,
  })
}
