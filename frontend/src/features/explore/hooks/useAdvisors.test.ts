import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { server } from '@/test/mocks/server'
import { MOCK_ADVISOR_DTOS } from '@/test/mocks/handlers/advisors'
import { createQueryWrapper, renderHook, waitFor } from '@/test/test-utils'
import { useAdvisors } from './useAdvisors'

describe('useAdvisors', () => {
  it('starts loading, then returns mapped advisors and paging totals', async () => {
    const { result } = renderHook(() => useAdvisors(), { wrapper: createQueryWrapper() })

    expect(result.current.isLoading).toBe(true)
    expect(result.current.advisors).toEqual([])

    await waitFor(() => expect(result.current.isLoading).toBe(false))

    expect(result.current.advisors).toHaveLength(MOCK_ADVISOR_DTOS.length)
    expect(result.current.totalElements).toBe(3)
    expect(result.current.totalPages).toBe(1)
    expect(result.current.isError).toBe(false)
    expect(result.current.errorMessage).toBeNull()
  })

  it('maps the wire DTO into the UI shape via mapAdvisor', async () => {
    const { result } = renderHook(() => useAdvisors(), { wrapper: createQueryWrapper() })

    await waitFor(() => expect(result.current.advisors).toHaveLength(3))

    const [maya] = result.current.advisors
    // Renamed fields.
    expect(maya.title).toBe('Career Transition Coach')
    expect(maya.rating).toBe(4.8)
    expect(maya.color).toBe('#005e8f')
    // Enum sectors become display labels.
    expect(maya.sectors).toEqual(['Career'])
    // And the DTO-only field names are gone.
    expect(maya).not.toHaveProperty('professionalTitle')
    expect(maya).not.toHaveProperty('averageRating')
  })

  it('translates multi-sector enums to labels', async () => {
    const { result } = renderHook(() => useAdvisors(), { wrapper: createQueryWrapper() })

    await waitFor(() => expect(result.current.advisors).toHaveLength(3))

    const sam = result.current.advisors.find((a) => a.username === 'sam_okafor')
    expect(sam?.sectors).toEqual(['Mental Health', 'Life Coaching'])
  })

  it('passes sector, q and page through as query params', async () => {
    let params: URLSearchParams | undefined
    server.use(
      http.get('*/api/advisors', ({ request }) => {
        params = new URL(request.url).searchParams
        return HttpResponse.json({
          content: [],
          totalElements: 0,
          totalPages: 0,
          number: 2,
          size: 20,
        })
      }),
    )

    const { result } = renderHook(() => useAdvisors({ sector: 'FINANCE', q: 'debt', page: 2 }), {
      wrapper: createQueryWrapper(),
    })

    await waitFor(() => expect(result.current.isLoading).toBe(false))

    expect(params?.get('sector')).toBe('FINANCE')
    expect(params?.get('q')).toBe('debt')
    expect(params?.get('page')).toBe('2')
  })

  it('omits undefined params rather than serialising them as "undefined"', async () => {
    let url: string | undefined
    server.use(
      http.get('*/api/advisors', ({ request }) => {
        url = request.url
        return HttpResponse.json({
          content: [],
          totalElements: 0,
          totalPages: 0,
          number: 0,
          size: 20,
        })
      }),
    )

    const { result } = renderHook(() => useAdvisors(), { wrapper: createQueryWrapper() })
    await waitFor(() => expect(result.current.isLoading).toBe(false))

    expect(url).not.toContain('undefined')
    expect(url).not.toContain('sector=')
  })

  it('filters server-side — the hook does no client-side filtering of its own', async () => {
    const { result } = renderHook(() => useAdvisors({ sector: 'FINANCE' }), {
      wrapper: createQueryWrapper(),
    })

    await waitFor(() => expect(result.current.isLoading).toBe(false))

    expect(result.current.advisors.map((a) => a.username)).toEqual(['rio_alvarez'])
    expect(result.current.totalElements).toBe(1)
  })

  it('reports an error with user-facing copy when the request fails', async () => {
    server.use(
      http.get('*/api/advisors', () =>
        HttpResponse.json({ message: 'Advisor service unavailable' }, { status: 500 }),
      ),
    )

    const { result } = renderHook(() => useAdvisors(), { wrapper: createQueryWrapper() })

    await waitFor(() => expect(result.current.isError).toBe(true))

    expect(result.current.advisors).toEqual([])
    expect(result.current.totalElements).toBe(0)
    expect(result.current.errorMessage).toBe('Advisor service unavailable')
  })
})
