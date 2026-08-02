import { useQuery } from '@tanstack/react-query'
import apiClient from '@/lib/axios'
import type { Message } from '@/types'
import type { MessageDto } from '@/types/api'

export const DEFAULT_MESSAGE_LIMIT = 50

export const messageHistoryQueryKey = (advisorId: string | undefined, limit: number) =>
  ['messages', advisorId, limit] as const

/** `GET /chats/{advisorId}/messages?limit=` — pending backend Phase 8. */
export async function fetchMessageHistory(advisorId: string, limit: number): Promise<Message[]> {
  const { data } = await apiClient.get<MessageDto[]>(
    `/chats/${encodeURIComponent(advisorId)}/messages`,
    { params: { limit } },
  )
  return data ?? []
}

/**
 * Backfill of an existing conversation, fetched once when a chat is opened.
 * Live messages arrive separately over the socket (`useChatSocket`); this is
 * only the history that predates the connection.
 */
export function useMessageHistory(advisorId: string | undefined, limit = DEFAULT_MESSAGE_LIMIT) {
  return useQuery({
    queryKey: messageHistoryQueryKey(advisorId, limit),
    queryFn: () => fetchMessageHistory(advisorId!, limit),
    enabled: !!advisorId,
  })
}
