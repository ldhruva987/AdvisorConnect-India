import { describe, expect, it, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { server } from '@/test/mocks/server'
import { MOCK_REVIEW_DTOS } from '@/test/mocks/handlers/advisors'
import { createQueryWrapper, renderHook, waitFor } from '@/test/test-utils'
import { useAdvisorReviews } from './useAdvisorReviews'

describe('useAdvisorReviews', () => {
  it('unwraps the Page envelope into reviews plus totals', async () => {
    const { result } = renderHook(() => useAdvisorReviews('advisor-1'), {
      wrapper: createQueryWrapper(),
    })

    expect(result.current.isLoading).toBe(true)

    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    expect(result.current.data?.reviews).toEqual(MOCK_REVIEW_DTOS)
    expect(result.current.data?.totalElements).toBe(2)
    expect(result.current.data?.totalPages).toBe(1)
  })

  it('does not fire until the advisor id is known', () => {
    const onRequest = vi.fn()
    server.events.on('request:start', onRequest)

    const { result } = renderHook(() => useAdvisorReviews(undefined), {
      wrapper: createQueryWrapper(),
    })

    expect(result.current.fetchStatus).toBe('idle')
    expect(onRequest).not.toHaveBeenCalled()

    server.events.removeListener('request:start', onRequest)
  })

  it('requests the page it was asked for', async () => {
    let params: URLSearchParams | undefined
    server.use(
      http.get('*/api/advisors/:advisorId/reviews', ({ request }) => {
        params = new URL(request.url).searchParams
        return HttpResponse.json({
          content: [],
          totalElements: 0,
          totalPages: 0,
          number: 3,
          size: 20,
        })
      }),
    )

    const { result } = renderHook(() => useAdvisorReviews('advisor-1', 3), {
      wrapper: createQueryWrapper(),
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(params?.get('page')).toBe('3')
  })

  it('tolerates a Page with no content array', async () => {
    server.use(
      http.get('*/api/advisors/:advisorId/reviews', () => HttpResponse.json({})),
    )

    const { result } = renderHook(() => useAdvisorReviews('advisor-1'), {
      wrapper: createQueryWrapper(),
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.reviews).toEqual([])
    expect(result.current.data?.totalElements).toBe(0)
  })
})
