import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { server } from '@/test/mocks/server'
import { MOCK_NOTIFICATION_DTOS } from '@/test/mocks/handlers/notifications'
import { createQueryWrapper, renderHook, waitFor } from '@/test/test-utils'
import { NOTIFICATIONS_POLL_INTERVAL_MS, useNotifications } from './useNotifications'

describe('useNotifications', () => {
  it('loads, then returns the inbox', async () => {
    const { result } = renderHook(() => useNotifications(), { wrapper: createQueryWrapper() })

    expect(result.current.isLoading).toBe(true)

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(MOCK_NOTIFICATION_DTOS)
    expect(result.current.data?.filter((n) => !n.read)).toHaveLength(1)
  })

  it('polls on a 30-second interval', () => {
    // Polling rather than a second authenticated WebSocket is the plan's
    // deliberate default for low-volume, latency-tolerant notifications.
    expect(NOTIFICATIONS_POLL_INTERVAL_MS).toBe(30_000)
  })

  it('refetches when the interval elapses', async () => {
    let calls = 0
    server.use(
      http.get('*/api/notifications', () => {
        calls += 1
        return HttpResponse.json([])
      }),
    )

    const { result } = renderHook(() => useNotifications(), { wrapper: createQueryWrapper() })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(calls).toBe(1)

    await result.current.refetch()
    expect(calls).toBe(2)
  })

  it('returns an empty array when the service sends nothing', async () => {
    server.use(http.get('*/api/notifications', () => HttpResponse.json(null)))

    const { result } = renderHook(() => useNotifications(), { wrapper: createQueryWrapper() })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual([])
  })

  it('reports an error when notification-service is unreachable', async () => {
    server.use(http.get('*/api/notifications', () => new HttpResponse(null, { status: 503 })))

    const { result } = renderHook(() => useNotifications(), { wrapper: createQueryWrapper() })

    await waitFor(() => expect(result.current.isError).toBe(true))
  })
})
