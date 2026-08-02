import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { getErrorMessage } from '@/lib/getErrorMessage'
import { server } from '@/test/mocks/server'
import { act, createQueryWrapper, renderHook, waitFor } from '@/test/test-utils'
import { useCreatePaymentIntent } from './useCreatePaymentIntent'

describe('useCreatePaymentIntent', () => {
  it('returns the client secret and server-computed amount', async () => {
    const { result } = renderHook(() => useCreatePaymentIntent(), {
      wrapper: createQueryWrapper(),
    })

    act(() => {
      result.current.mutate({
        advisorId: 'advisor-1',
        durationMinutes: 30,
        slot: '2026-08-14T09:00:00Z',
      })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.clientSecret).toBe('pi_test_secret_123')
    expect(result.current.data?.amount).toBe(4500)
  })

  it('takes the amount from the server rather than computing it client-side', async () => {
    const { result } = renderHook(() => useCreatePaymentIntent(), {
      wrapper: createQueryWrapper(),
    })

    act(() => {
      result.current.mutate({
        advisorId: 'advisor-1',
        durationMinutes: 60,
        slot: '2026-08-14T09:00:00Z',
      })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.amount).toBe(9000)
  })

  it('sends advisorId, durationMinutes and slot', async () => {
    let body: unknown
    server.use(
      http.post('*/api/bookings/payment-intent', async ({ request }) => {
        body = await request.json()
        return HttpResponse.json({ clientSecret: 'x', amount: 4500 })
      }),
    )

    const { result } = renderHook(() => useCreatePaymentIntent(), {
      wrapper: createQueryWrapper(),
    })

    act(() => {
      result.current.mutate({
        advisorId: 'advisor-1',
        durationMinutes: 30,
        slot: '2026-08-14T09:00:00Z',
      })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(body).toEqual({
      advisorId: 'advisor-1',
      durationMinutes: 30,
      slot: '2026-08-14T09:00:00Z',
    })
  })

  it('surfaces a rejected duration from the backend', async () => {
    server.use(
      http.post('*/api/bookings/payment-intent', () =>
        HttpResponse.json({ message: 'Duration must be 30 or 60 minutes' }, { status: 400 }),
      ),
    )

    const { result } = renderHook(() => useCreatePaymentIntent(), {
      wrapper: createQueryWrapper(),
    })

    act(() => {
      result.current.mutate({
        advisorId: 'advisor-1',
        durationMinutes: 30,
        slot: '2026-08-14T09:00:00Z',
      })
    })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(getErrorMessage(result.current.error)).toBe('Duration must be 30 or 60 minutes')
  })
})
