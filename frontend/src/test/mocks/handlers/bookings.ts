import { http, HttpResponse } from 'msw'
import type { Booking } from '@/types'
import type { CreateBookingRequest } from '@/types/api'

/**
 * Availability / payment intent / booking creation / my bookings.
 *
 * `GET /bookings/availability/{advisorId}` and `POST /bookings` are real today;
 * `POST /bookings/payment-intent` and `GET /bookings/me` arrive with backend
 * Phases 4 and 3.
 *
 * Note the availability contract is a bare `string[]` of *available* ISO slot
 * instants — there is no per-slot `taken` flag anywhere in the backend, so
 * nothing here invents one.
 */

export const MOCK_AVAILABLE_SLOTS: string[] = [
  '2026-08-14T09:00:00Z',
  '2026-08-14T09:30:00Z',
  '2026-08-14T14:00:00Z',
]

/** The date the fixture slots belong to; any other date comes back empty. */
export const MOCK_AVAILABILITY_DATE = '2026-08-14'

export const MOCK_BOOKINGS: Booking[] = [
  {
    id: 'booking-1',
    advisorId: 'advisor-1',
    advisorUsername: 'maya_chen',
    sessionDate: '2026-08-14T09:00:00Z',
    durationMinutes: 30,
    amountCharged: 4500,
    status: 'CONFIRMED',
    createdAt: '2026-08-01T08:00:00Z',
  },
  {
    id: 'booking-2',
    advisorId: 'advisor-2',
    advisorUsername: 'sam_okafor',
    sessionDate: '2026-07-02T15:00:00Z',
    durationMinutes: 60,
    amountCharged: 9000,
    status: 'COMPLETED',
    createdAt: '2026-06-28T11:20:00Z',
  },
]

/** The booking `POST /bookings` echoes back, in today's bare-`Booking` shape. */
export const MOCK_CREATED_BOOKING: Booking = {
  id: 'booking-new',
  advisorId: 'advisor-1',
  advisorUsername: 'maya_chen',
  sessionDate: '2026-08-14T09:00:00Z',
  durationMinutes: 30,
  amountCharged: 4500,
  status: 'PENDING',
  createdAt: '2026-08-01T12:00:00Z',
}

export const bookingHandlers = [
  http.get('*/api/bookings/availability/:advisorId', ({ request }) => {
    const date = new URL(request.url).searchParams.get('date')
    return HttpResponse.json(date === MOCK_AVAILABILITY_DATE ? MOCK_AVAILABLE_SLOTS : [])
  }),

  http.get('*/api/bookings/me', () => HttpResponse.json(MOCK_BOOKINGS)),

  http.post('*/api/bookings/payment-intent', async ({ request }) => {
    const body = (await request.json()) as { durationMinutes: number }
    return HttpResponse.json({
      clientSecret: 'pi_test_secret_123',
      // 30 min = $45.00, 60 min = $90.00, in cents. Mirrors backend pricing.
      amount: body.durationMinutes === 60 ? 9000 : 4500,
    })
  }),

  http.post('*/api/bookings', async ({ request }) => {
    const body = (await request.json()) as CreateBookingRequest
    return HttpResponse.json(
      {
        ...MOCK_CREATED_BOOKING,
        advisorId: body.advisorId,
        sessionDate: body.sessionDateTime,
        durationMinutes: body.durationMinutes,
      } satisfies Booking,
      { status: 201 },
    )
  }),
]
