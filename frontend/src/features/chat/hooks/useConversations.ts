import { useQuery } from '@tanstack/react-query'
import apiClient from '@/lib/axios'
import type { Conversation } from '@/types'
import type { ConversationDto } from '@/types/api'

export const conversationsQueryKey = () => ['conversations'] as const

/** `GET /chats` — pending backend Phase 8 (chat-service has no REST layer today). */
export async function fetchConversations(): Promise<Conversation[]> {
  const { data } = await apiClient.get<ConversationDto[]>('/chats')
  return data ?? []
}

/**
 * The signed-in user's conversation list (ChatPage's sidebar,
 * AdvisorDashboardPage's Chats tab).
 *
 * `ConversationDto` and the UI's `Conversation` are field-for-field identical,
 * so this deliberately has no mapper — unlike advisors, there is no
 * wire-vs-UI divergence to absorb.
 *
 * Phase 2 TODO: ChatPage will push this into `useChatStore.setConversations`.
 * That's left to the page rather than done here so the hook stays a pure data
 * source with no hidden global write, and so a component can read the list
 * without silently clobbering another one's store state.
 */
export function useConversations() {
  return useQuery({
    queryKey: conversationsQueryKey(),
    queryFn: fetchConversations,
  })
}
