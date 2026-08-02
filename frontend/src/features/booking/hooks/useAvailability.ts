import { useQuery } from '@tanstack/react-query'
import apiClient from '@/lib/axios'

export const availabilityQueryKey = (advisorId: string | undefined, dateStr: string | undefined) =>
  ['availability', advisorId, dateStr] as const

/** `GET /bookings/availability/{advisorId}?date=YYYY-MM-DD`. Real backend endpoint. */
export async function fetchAvailability(advisorId: string, dateStr: string): Promise<string[]> {
  const { data } = await apiClient.get<string[]>(
    `/bookings/availability/${encodeURIComponent(advisorId)}`,
    { params: { date: dateStr } },
  )
  return data ?? []
}

/**
 * Available slots for one advisor on one day.
 *
 * Returns the backend's bare `string[]` of ISO instants unchanged. The old
 * mock BookingPage rendered a `{ time, taken }` shape, and `types/index.ts`
 * still carries a `TimeSlot` interface with a `taken` boolean — but no such
 * concept exists on the backend, which only ever reports what IS free. Slots
 * are not returned-and-flagged; unavailable ones are simply absent.
 * Synthesising `taken: false` for every entry here would be inventing a field
 * the UI could then be tempted to trust.
 */
export function useAvailability(advisorId: string | undefined, dateStr: string | undefined) {
  return useQuery({
    queryKey: availabilityQueryKey(advisorId, dateStr),
    queryFn: () => fetchAvailability(advisorId!, dateStr!),
    enabled: !!advisorId && !!dateStr,
  })
}
