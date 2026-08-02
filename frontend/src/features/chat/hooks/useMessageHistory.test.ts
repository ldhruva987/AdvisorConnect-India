import { describe, expect, it, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { server } from '@/test/mocks/server'
import { createQueryWrapper, renderHook, waitFor } from '@/test/test-utils'
import { useMessageHistory } from './useMessageHistory'

describe('useMessageHistory', () => {
  it('returns the backfilled messages for the advisor’s conversation', async () => {
    const { result } = renderHook(() => useMessageHistory('advisor-1'), {
      wrapper: createQueryWrapper(),
    })

    expect(result.current.isLoading).toBe(true)

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toHaveLength(2)
    expect(result.current.data?.map((m) => m.senderType)).toEqual(['user', 'advisor'])
  })

  it('scopes history to the requested advisor', async () => {
    const { result } = renderHook(() => useMessageHistory('advisor-2'), {
      wrapper: createQueryWrapper(),
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual([])
  })

  it('sends the default limit, and an explicit one when given', async () => {
    const seen: string[] = []
    server.use(
      http.get('*/api/chats/:advisorId/messages', ({ request }) => {
        seen.push(new URL(request.url).searchParams.get('limit') ?? '')
        return HttpResponse.json([])
      }),
    )

    const wrapper = createQueryWrapper()
    const first = renderHook(() => useMessageHistory('advisor-1'), { wrapper })
    await waitFor(() => expect(first.result.current.isSuccess).toBe(true))

    const second = renderHook(() => useMessageHistory('advisor-1', 10), {
      wrapper: createQueryWrapper(),
    })
    await waitFor(() => expect(second.result.current.isSuccess).toBe(true))

    expect(seen).toEqual(['50', '10'])
  })

  it('stays idle until the advisor id is known', () => {
    const onRequest = vi.fn()
    server.events.on('request:start', onRequest)

    const { result } = renderHook(() => useMessageHistory(undefined), {
      wrapper: createQueryWrapper(),
    })

    expect(result.current.fetchStatus).toBe('idle')
    expect(onRequest).not.toHaveBeenCalled()

    server.events.removeListener('request:start', onRequest)
  })
})
