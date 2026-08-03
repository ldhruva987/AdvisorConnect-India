import { http, HttpResponse } from 'msw'
import type { Booking } from '@/types'
import type { CreateBookingRequest } from '@/types/api'

/**
 * Availability / booking creation / my bookings.
 *
 * All three are real backend endpoints today.
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
    amountCharged: 500,
    status: 'CONFIRMED',
    createdAt: '2026-08-01T08:00:00Z',
  },
  {
    id: 'booking-2',
    advisorId: 'advisor-2',
    advisorUsername: 'sam_okafor',
    sessionDate: '2026-07-02T15:00:00Z',
    durationMinutes: 60,
    amountCharged: 900,
    status: 'COMPLETED',
    createdAt: '2026-06-28T11:20:00Z',
  },
]

/** The booking `POST /bookings` echoes back, nested under `booking` in the real response shape. */
export const MOCK_CREATED_BOOKING: Booking = {
  id: 'booking-new',
  advisorId: 'advisor-1',
  advisorUsername: 'maya_chen',
  sessionDate: '2026-08-14T09:00:00Z',
  durationMinutes: 30,
  amountCharged: 500,
  status: 'PENDING',
  createdAt: '2026-08-01T12:00:00Z',
}

/** The Razorpay order id `POST /bookings` returns alongside `MOCK_CREATED_BOOKING`. */
export const MOCK_RAZORPAY_ORDER_ID = 'order_test'

export const bookingHandlers = [
  http.get('*/api/bookings/availability/:advisorId', ({ request }) => {
    const date = new URL(request.url).searchParams.get('date')
    return HttpResponse.json(date === MOCK_AVAILABILITY_DATE ? MOCK_AVAILABLE_SLOTS : [])
  }),

  http.get('*/api/bookings/me', () => HttpResponse.json(MOCK_BOOKINGS)),

  http.post('*/api/bookings', async ({ request }) => {
    const body = (await request.json()) as CreateBookingRequest
    return HttpResponse.json(
      {
        booking: {
          ...MOCK_CREATED_BOOKING,
          advisorId: body.advisorId,
          sessionDate: body.sessionDateTime,
          durationMinutes: body.durationMinutes,
        },
        razorpayOrderId: MOCK_RAZORPAY_ORDER_ID,
      },
      { status: 201 },
    )
  }),
]
