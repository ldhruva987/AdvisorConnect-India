import { describe, expect, it, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { getErrorMessage } from '@/lib/getErrorMessage'
import { server } from '@/test/mocks/server'
import { MOCK_CREATED_BOOKING } from '@/test/mocks/handlers/bookings'
import type { Booking } from '@/types'
import {
  act,
  createQueryWrapper,
  createTestQueryClient,
  renderHook,
  waitFor,
} from '@/test/test-utils'
import { normalizeCreateBookingResponse, useCreateBooking } from './useCreateBooking'

const VARIABLES = {
  advisorId: 'advisor-1',
  sessionDateTime: '2026-08-14T09:00:00Z',
  durationMinutes: 30,
} as const

describe('normalizeCreateBookingResponse', () => {
  // This function is the single seam absorbing the Stripe migration, so it is
  // tested directly rather than only through the hook.
  it("wraps today's bare Booking response with a null clientSecret", () => {
    const result = normalizeCreateBookingResponse(MOCK_CREATED_BOOKING)
    expect(result.booking).toEqual(MOCK_CREATED_BOOKING)
    expect(result.clientSecret).toBeNull()
  })

  it("unwraps the post-Phase-4 { booking, clientSecret } response", () => {
    const result = normalizeCreateBookingResponse({
      booking: MOCK_CREATED_BOOKING,
      clientSecret: 'pi_abc_secret',
    })
    expect(result.booking).toEqual(MOCK_CREATED_BOOKING)
    expect(result.clientSecret).toBe('pi_abc_secret')
  })
})

describe('useCreateBooking', () => {
  it('creates a booking and echoes back the submitted slot', async () => {
    const { result } = renderHook(() => useCreateBooking(), { wrapper: createQueryWrapper() })

    act(() => {
      result.current.mutate(VARIABLES)
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.booking.advisorId).toBe('advisor-1')
    expect(result.current.data?.booking.sessionDate).toBe('2026-08-14T09:00:00Z')
    expect(result.current.data?.booking.status).toBe('PENDING')
    expect(result.current.data?.clientSecret).toBeNull()
  })

  it('transparently handles the future { booking, clientSecret } shape', async () => {
    // Proves the Phase 4 switchover needs no change outside the normaliser.
    server.use(
      http.post('*/api/bookings', () =>
        HttpResponse.json(
          { booking: MOCK_CREATED_BOOKING, clientSecret: 'pi_live_secret' },
          { status: 201 },
        ),
      ),
    )

    const { result } = renderHook(() => useCreateBooking(), { wrapper: createQueryWrapper() })

    act(() => {
      result.current.mutate(VARIABLES)
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.booking.id).toBe(MOCK_CREATED_BOOKING.id)
    expect(result.current.data?.clientSecret).toBe('pi_live_secret')
  })

  it('invalidates my-bookings and the advisor’s availability', async () => {
    const queryClient = createTestQueryClient()
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries')

    const { result } = renderHook(() => useCreateBooking(), {
      wrapper: createQueryWrapper(queryClient),
    })

    act(() => {
      result.current.mutate(VARIABLES)
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['bookings', 'me'] })
    // The slot just taken must stop being offered.
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['availability', 'advisor-1'] })
  })

  it('surfaces a slot-conflict 409 with the backend message', async () => {
    server.use(
      http.post('*/api/bookings', () =>
        HttpResponse.json({ message: 'That slot is no longer available' }, { status: 409 }),
      ),
    )

    const { result } = renderHook(() => useCreateBooking(), { wrapper: createQueryWrapper() })

    act(() => {
      result.current.mutate(VARIABLES)
    })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(result.current.data).toBeUndefined()
    expect(getErrorMessage(result.current.error)).toBe('That slot is no longer available')
  })

  it('surfaces a 500 as generic copy and does not invalidate anything', async () => {
    server.use(http.post('*/api/bookings', () => new HttpResponse(null, { status: 500 })))

    const queryClient = createTestQueryClient()
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries')

    const { result } = renderHook(() => useCreateBooking(), {
      wrapper: createQueryWrapper(queryClient),
    })

    act(() => {
      result.current.mutate(VARIABLES)
    })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(getErrorMessage(result.current.error)).toBe('Something went wrong. Please try again.')
    // A failed booking must not evict a still-valid availability list.
    expect(invalidate).not.toHaveBeenCalled()
  })

  it('passes stripePaymentMethodId through when supplied', async () => {
    let body: Booking & { stripePaymentMethodId?: string }
    server.use(
      http.post('*/api/bookings', async ({ request }) => {
        body = (await request.json()) as typeof body
        return HttpResponse.json(MOCK_CREATED_BOOKING, { status: 201 })
      }),
    )

    const { result } = renderHook(() => useCreateBooking(), { wrapper: createQueryWrapper() })

    act(() => {
      result.current.mutate({ ...VARIABLES, stripePaymentMethodId: 'pm_card_visa' })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(body!.stripePaymentMethodId).toBe('pm_card_visa')
  })
})
