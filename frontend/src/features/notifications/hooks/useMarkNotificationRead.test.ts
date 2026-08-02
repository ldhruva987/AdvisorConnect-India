import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { server } from '@/test/mocks/server'
import { MOCK_NOTIFICATION_DTOS } from '@/test/mocks/handlers/notifications'
import type { NotificationDto } from '@/types/api'
import {
  act,
  createQueryWrapper,
  createTestQueryClient,
  renderHook,
  waitFor,
} from '@/test/test-utils'
import { useMarkNotificationRead } from './useMarkNotificationRead'
import { useNotifications } from './useNotifications'

describe('useMarkNotificationRead', () => {
  it('marks the notification read on the server', async () => {
    const { result } = renderHook(() => useMarkNotificationRead(), {
      wrapper: createQueryWrapper(),
    })

    act(() => {
      result.current.mutate('notification-1')
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.id).toBe('notification-1')
    expect(result.current.data?.read).toBe(true)
  })

  it('PUTs to the id-scoped read path', async () => {
    let path: string | undefined
    server.use(
      http.put('*/api/notifications/:id/read', ({ request }) => {
        path = new URL(request.url).pathname
        return HttpResponse.json(MOCK_NOTIFICATION_DTOS[0])
      }),
    )

    const { result } = renderHook(() => useMarkNotificationRead(), {
      wrapper: createQueryWrapper(),
    })

    act(() => {
      result.current.mutate('notification-1')
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(path).toBe('/api/notifications/notification-1/read')
  })

  /**
   * Mount the list alongside the mutation, the way the Navbar bell does.
   *
   * Not just realism: the test QueryClient uses `gcTime: 0`, so a cache entry
   * seeded with `setQueryData` and no observer is collected immediately. An
   * observer is what keeps the entry alive long enough to assert on.
   */
  function renderBell(queryClient = createTestQueryClient()) {
    return renderHook(
      () => ({ list: useNotifications(), mark: useMarkNotificationRead() }),
      { wrapper: createQueryWrapper(queryClient) },
    )
  }

  const readFlag = (list: NotificationDto[] | undefined) =>
    list?.find((n) => n.id === 'notification-1')?.read

  it('optimistically clears the unread flag before the server replies', async () => {
    // Hold the response open so the optimistic state is observable.
    let release: () => void = () => {}
    const inFlight = new Promise<void>((resolve) => {
      release = resolve
    })
    server.use(
      http.put('*/api/notifications/:id/read', async () => {
        await inFlight
        return HttpResponse.json({ ...MOCK_NOTIFICATION_DTOS[0], read: true })
      }),
    )

    const { result } = renderBell()
    await waitFor(() => expect(result.current.list.isSuccess).toBe(true))
    expect(readFlag(result.current.list.data)).toBe(false)

    act(() => {
      result.current.mark.mutate('notification-1')
    })

    // Flipped already, with the request still in flight.
    await waitFor(() => expect(readFlag(result.current.list.data)).toBe(true))
    expect(result.current.mark.isPending).toBe(true)

    release()
    await waitFor(() => expect(result.current.mark.isSuccess).toBe(true))
  })

  it('rolls the optimistic update back when the request fails', async () => {
    server.use(
      http.put('*/api/notifications/:id/read', () => new HttpResponse(null, { status: 500 })),
    )

    const { result } = renderBell()
    await waitFor(() => expect(result.current.list.isSuccess).toBe(true))

    act(() => {
      result.current.mark.mutate('notification-1')
    })

    await waitFor(() => expect(result.current.mark.isError).toBe(true))

    // Restored from the onMutate snapshot, and the onSettled refetch (which
    // still reports read:false) must not resurrect the optimistic value.
    await waitFor(() => expect(readFlag(result.current.list.data)).toBe(false))
  })

  it('surfaces a 404 for an unknown notification', async () => {
    const { result } = renderHook(() => useMarkNotificationRead(), {
      wrapper: createQueryWrapper(),
    })

    act(() => {
      result.current.mutate('notification-999')
    })

    await waitFor(() => expect(result.current.isError).toBe(true))
  })
})
