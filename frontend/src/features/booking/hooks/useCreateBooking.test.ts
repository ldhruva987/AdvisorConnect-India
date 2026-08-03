import { describe, expect, it, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { getErrorMessage } from '@/lib/getErrorMessage'
import { server } from '@/test/mocks/server'
import { MOCK_RAZORPAY_ORDER_ID } from '@/test/mocks/handlers/bookings'
import {
  act,
  createQueryWrapper,
  createTestQueryClient,
  renderHook,
  waitFor,
} from '@/test/test-utils'
import { useCreateBooking } from './useCreateBooking'

const VARIABLES = {
  advisorId: 'advisor-1',
  sessionDateTime: '2026-08-14T09:00:00Z',
  durationMinutes: 30,
} as const

describe('useCreateBooking', () => {
  it('creates a booking and returns the booking plus the Razorpay order id', async () => {
    const { result } = renderHook(() => useCreateBooking(), { wrapper: createQueryWrapper() })

    act(() => {
      result.current.mutate(VARIABLES)
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.booking.advisorId).toBe('advisor-1')
    expect(result.current.data?.booking.sessionDate).toBe('2026-08-14T09:00:00Z')
    expect(result.current.data?.booking.status).toBe('PENDING')
    expect(result.current.data?.razorpayOrderId).toBe(MOCK_RAZORPAY_ORDER_ID)
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
})
