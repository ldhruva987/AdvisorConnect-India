import { useMutation, type UseMutationResult } from '@tanstack/react-query'
import apiClient from '@/lib/axios'
import type { CreatePaymentIntentRequest, PaymentIntentResponse } from '@/types/api'

/** `POST /bookings/payment-intent` — pending backend Phase 4 (real Stripe). */
export async function postPaymentIntent(
  body: CreatePaymentIntentRequest,
): Promise<PaymentIntentResponse> {
  const { data } = await apiClient.post<PaymentIntentResponse>('/bookings/payment-intent', body)
  return data
}

/**
 * Create the Stripe PaymentIntent for a pending booking.
 *
 * Called before booking creation: BookingPage needs the `clientSecret` to
 * mount `<PaymentElement>` and run `confirmPayment`. The `amount` comes back
 * from the server rather than being computed client-side on purpose — price is
 * a server decision (the backend rejects any duration other than 30/60), and a
 * client-computed total that disagreed with the charge would be worse than no
 * total at all.
 */
export function useCreatePaymentIntent(): UseMutationResult<
  PaymentIntentResponse,
  Error,
  CreatePaymentIntentRequest
> {
  return useMutation({ mutationFn: postPaymentIntent })
}
