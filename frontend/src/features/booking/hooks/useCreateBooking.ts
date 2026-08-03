import { useMutation, useQueryClient, type UseMutationResult } from '@tanstack/react-query'
import apiClient from '@/lib/axios'
import type { CreateBookingRequest, CreateBookingResponse } from '@/types/api'

/**
 * `POST /bookings`. REAL backend endpoint.
 *
 * Returns the freshly created (still-unpaid) booking together with the Razorpay order id the
 * page needs to open Checkout. There is no other shape this endpoint has ever returned — unlike
 * Stripe's client secret, a Razorpay order id is not a short-lived value that only exists once a
 * separate "create the payment intent" call has happened first.
 */
export async function postBooking(body: CreateBookingRequest): Promise<CreateBookingResponse> {
  const { data } = await apiClient.post<CreateBookingResponse>('/bookings', body)
  return data
}

/**
 * Create a booking.
 *
 * Invalidates the caller's booking list and the advisor's availability: the
 * slot just taken must stop being offered, otherwise a user who backs out to
 * the calendar sees the slot they just booked still shown as free.
 */
export function useCreateBooking(): UseMutationResult<
  CreateBookingResponse,
  Error,
  CreateBookingRequest
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: postBooking,
    onSuccess: (_result, variables) => {
      void queryClient.invalidateQueries({ queryKey: ['bookings', 'me'] })
      void queryClient.invalidateQueries({ queryKey: ['availability', variables.advisorId] })
    },
  })
}
