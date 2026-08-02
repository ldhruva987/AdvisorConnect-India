import { describe, expect, it, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { getErrorMessage } from '@/lib/getErrorMessage'
import { server } from '@/test/mocks/server'
import {
  act,
  createQueryWrapper,
  createTestQueryClient,
  renderHook,
  waitFor,
} from '@/test/test-utils'
import { useSubmitReview } from './useSubmitReview'

describe('useSubmitReview', () => {
  it('posts the review and returns the created record', async () => {
    const { result } = renderHook(() => useSubmitReview('advisor-1'), {
      wrapper: createQueryWrapper(),
    })

    act(() => {
      result.current.mutate({ bookingId: 'booking-2', rating: 5, text: 'Genuinely helpful.' })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.rating).toBe(5)
    expect(result.current.data?.text).toBe('Genuinely helpful.')
  })

  it('sends bookingId in the body — the backend gates on a completed booking', async () => {
    let body: unknown
    server.use(
      http.post('*/api/advisors/:advisorId/reviews', async ({ request }) => {
        body = await request.json()
        return HttpResponse.json({ id: 'r', reviewerUsername: 'u', reviewerColor: '#000', rating: 4, text: 't', createdAt: '2026-08-01T00:00:00Z' })
      }),
    )

    const { result } = renderHook(() => useSubmitReview('advisor-1'), {
      wrapper: createQueryWrapper(),
    })

    act(() => {
      result.current.mutate({ bookingId: 'booking-2', rating: 4, text: 't' })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(body).toEqual({ bookingId: 'booking-2', rating: 4, text: 't' })
  })

  it('invalidates both the review list and the advisor profile', async () => {
    // The profile carries averageRating/reviewCount, which the backend
    // recomputes in the same transaction — leaving it cached would show a
    // stale rating beside the review that just changed it.
    const queryClient = createTestQueryClient()
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries')

    const { result } = renderHook(() => useSubmitReview('advisor-1'), {
      wrapper: createQueryWrapper(queryClient),
    })

    act(() => {
      result.current.mutate({ bookingId: 'booking-2', rating: 5, text: 'Great.' })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['advisor-reviews', 'advisor-1'] })
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['advisor'] })
  })

  it('surfaces a rejection when the booking is not eligible', async () => {
    server.use(
      http.post('*/api/advisors/:advisorId/reviews', () =>
        HttpResponse.json({ message: 'No completed booking to review' }, { status: 409 }),
      ),
    )

    const { result } = renderHook(() => useSubmitReview('advisor-1'), {
      wrapper: createQueryWrapper(),
    })

    act(() => {
      result.current.mutate({ bookingId: 'booking-1', rating: 5, text: 'Nope.' })
    })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(getErrorMessage(result.current.error)).toBe('No completed booking to review')
  })
})
