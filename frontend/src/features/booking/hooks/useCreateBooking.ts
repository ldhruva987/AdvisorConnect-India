import { useMutation, useQueryClient, type UseMutationResult } from '@tanstack/react-query'
import apiClient from '@/lib/axios'
import type { Booking } from '@/types'
import type { CreateBookingRequest, CreateBookingResponse } from '@/types/api'

export interface CreatedBooking {
  booking: Booking
  /**
   * Present only once backend Phase 4 lands. `null` against today's backend,
   * which creates the booking without a real PaymentIntent.
   */
  clientSecret: string | null
}

/**
 * THE one place that knows `POST /bookings` is mid-migration.
 *
 * Today the endpoint returns a bare `Booking`. After backend Phase 4 it
 * returns `{ booking, clientSecret }`. Rather than guess a date and break on
 * either side of it, this accepts both and normalises. When the backend
 * settles, deleting the `'booking' in raw` branch is the entire change — no
 * caller, page, or test outside this file is coupled to the wire shape.
 */
export function normalizeCreateBookingResponse(raw: CreateBookingResponse): CreatedBooking {
  if (raw && typeof raw === 'object' && 'booking' in raw) {
    return { booking: raw.booking, clientSecret: raw.clientSecret ?? null }
  }
  return { booking: raw, clientSecret: null }
}

/** `POST /bookings`. Real backend endpoint (response shape pending Phase 4). */
export async function postBooking(body: CreateBookingRequest): Promise<CreatedBooking> {
  const { data } = await apiClient.post<CreateBookingResponse>('/bookings', body)
  return normalizeCreateBookingResponse(data)
}

/**
 * Create a booking.
 *
 * Invalidates the caller's booking list and the advisor's availability: the
 * slot just taken must stop being offered, otherwise a user who backs out to
 * the calendar sees the slot they just booked still shown as free.
 */
export function useCreateBooking(): UseMutationResult<CreatedBooking, Error, CreateBookingRequest> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: postBooking,
    onSuccess: (_result, variables) => {
      void queryClient.invalidateQueries({ queryKey: ['bookings', 'me'] })
      void queryClient.invalidateQueries({ queryKey: ['availability', variables.advisorId] })
    },
  })
}
