import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { server } from '@/test/mocks/server'
import { MOCK_CONVERSATION_DTOS } from '@/test/mocks/handlers/chat'
import { useChatStore } from '@/stores/chatStore'
import { createQueryWrapper, renderHook, waitFor } from '@/test/test-utils'
import { useConversations } from './useConversations'

describe('useConversations', () => {
  it('loads, then returns the conversation list', async () => {
    const { result } = renderHook(() => useConversations(), { wrapper: createQueryWrapper() })

    expect(result.current.isLoading).toBe(true)

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(MOCK_CONVERSATION_DTOS)
    expect(result.current.data?.[0].unreadCount).toBe(2)
  })

  it('does not write into the chat store — that is the page’s job in Phase 2', async () => {
    const { result } = renderHook(() => useConversations(), { wrapper: createQueryWrapper() })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(useChatStore.getState().conversations).toEqual([])
  })

  it('returns an empty array when the service sends nothing', async () => {
    server.use(http.get('*/api/chats', () => HttpResponse.json(null)))

    const { result } = renderHook(() => useConversations(), { wrapper: createQueryWrapper() })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual([])
  })

  it('reports an error when chat-service is unreachable', async () => {
    server.use(http.get('*/api/chats', () => new HttpResponse(null, { status: 502 })))

    const { result } = renderHook(() => useConversations(), { wrapper: createQueryWrapper() })

    await waitFor(() => expect(result.current.isError).toBe(true))
  })
})
