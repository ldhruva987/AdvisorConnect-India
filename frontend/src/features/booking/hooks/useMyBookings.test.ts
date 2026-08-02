import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { server } from '@/test/mocks/server'
import { MOCK_BOOKINGS } from '@/test/mocks/handlers/bookings'
import { createQueryWrapper, renderHook, waitFor } from '@/test/test-utils'
import { useMyBookings } from './useMyBookings'

describe('useMyBookings', () => {
  it('loads, then returns the caller’s bookings', async () => {
    const { result } = renderHook(() => useMyBookings(), { wrapper: createQueryWrapper() })

    expect(result.current.isLoading).toBe(true)

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(MOCK_BOOKINGS)
    expect(result.current.data?.map((b) => b.status)).toEqual(['CONFIRMED', 'COMPLETED'])
  })

  it('returns an empty array rather than undefined for a user with no bookings', async () => {
    server.use(http.get('*/api/bookings/me', () => HttpResponse.json(null)))

    const { result } = renderHook(() => useMyBookings(), { wrapper: createQueryWrapper() })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual([])
  })

  it('reports an error when the service is down', async () => {
    server.use(http.get('*/api/bookings/me', () => new HttpResponse(null, { status: 503 })))

    const { result } = renderHook(() => useMyBookings(), { wrapper: createQueryWrapper() })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(result.current.data).toBeUndefined()
  })
})
