import { describe, expect, it, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { server } from '@/test/mocks/server'
import { MOCK_AVAILABILITY_DATE, MOCK_AVAILABLE_SLOTS } from '@/test/mocks/handlers/bookings'
import { createQueryWrapper, renderHook, waitFor } from '@/test/test-utils'
import { useAvailability } from './useAvailability'

describe('useAvailability', () => {
  it('returns the raw array of available ISO slot times', async () => {
    const { result } = renderHook(() => useAvailability('advisor-1', MOCK_AVAILABILITY_DATE), {
      wrapper: createQueryWrapper(),
    })

    expect(result.current.isLoading).toBe(true)

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(MOCK_AVAILABLE_SLOTS)
  })

  it('does not invent a `taken` flag — the backend only reports free slots', async () => {
    const { result } = renderHook(() => useAvailability('advisor-1', MOCK_AVAILABILITY_DATE), {
      wrapper: createQueryWrapper(),
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    // Every entry must stay a bare string. `types/index.ts` still carries a
    // `TimeSlot { time, taken }` shape left over from the mock UI, and mapping
    // into it here would fabricate a field the API never sends.
    for (const slot of result.current.data ?? []) {
      expect(typeof slot).toBe('string')
    }
  })

  it('sends the date as a query param on the advisor-scoped path', async () => {
    let url: URL | undefined
    server.use(
      http.get('*/api/bookings/availability/:advisorId', ({ request }) => {
        url = new URL(request.url)
        return HttpResponse.json([])
      }),
    )

    const { result } = renderHook(() => useAvailability('advisor-7', '2026-09-01'), {
      wrapper: createQueryWrapper(),
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(url?.pathname).toBe('/api/bookings/availability/advisor-7')
    expect(url?.searchParams.get('date')).toBe('2026-09-01')
  })

  it('returns an empty list for a fully-booked day', async () => {
    const { result } = renderHook(() => useAvailability('advisor-1', '2026-08-15'), {
      wrapper: createQueryWrapper(),
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual([])
  })

  it.each([
    ['no advisor', undefined, '2026-08-14'],
    ['no date', 'advisor-1', undefined],
    ['neither', undefined, undefined],
  ])('stays idle with %s', (_label, advisorId, dateStr) => {
    const onRequest = vi.fn()
    server.events.on('request:start', onRequest)

    const { result } = renderHook(() => useAvailability(advisorId, dateStr), {
      wrapper: createQueryWrapper(),
    })

    expect(result.current.fetchStatus).toBe('idle')
    expect(onRequest).not.toHaveBeenCalled()

    server.events.removeListener('request:start', onRequest)
  })
})
