import { http, HttpResponse } from 'msw'
import type { ConversationDto, MessageDto } from '@/types/api'

/**
 * Conversation list + message history — both pending backend Phase 8
 * (chat-service has no REST controller at all today; the gateway already
 * routes `/api/chats/**` there with nothing listening).
 *
 * The live WebSocket channel is *not* mocked here: MSW does not intercept raw
 * WebSockets, so `useChatSocket.test.ts` installs its own fake `WebSocket` on
 * `globalThis` instead.
 */

export const MOCK_CONVERSATION_DTOS: ConversationDto[] = [
  {
    id: 'conversation-1',
    advisorId: 'advisor-1',
    advisorUsername: 'maya_chen',
    advisorColor: '#005e8f',
    lastMessage: 'Sounds good — talk Thursday.',
    lastMessageAt: '2026-07-31T18:04:00Z',
    unreadCount: 2,
    isAdvisorOnline: true,
  },
  {
    id: 'conversation-2',
    advisorId: 'advisor-2',
    advisorUsername: 'sam_okafor',
    advisorColor: '#4d4f94',
    lastMessage: 'Try the breathing exercise tonight.',
    lastMessageAt: '2026-07-28T20:15:00Z',
    unreadCount: 0,
    isAdvisorOnline: false,
  },
]

export const MOCK_MESSAGE_DTOS: MessageDto[] = [
  {
    id: 'message-1',
    conversationId: 'conversation-1',
    senderId: 'user-1',
    senderType: 'user',
    text: 'Is Thursday still okay?',
    createdAt: '2026-07-31T18:00:00Z',
  },
  {
    id: 'message-2',
    conversationId: 'conversation-1',
    senderId: 'advisor-1',
    senderType: 'advisor',
    text: 'Sounds good — talk Thursday.',
    createdAt: '2026-07-31T18:04:00Z',
  },
]

export const chatHandlers = [
  http.get('*/api/chats/:advisorId/messages', ({ params, request }) => {
    const limit = Number(new URL(request.url).searchParams.get('limit') ?? 50)
    const messages = MOCK_MESSAGE_DTOS.filter(
      (m) => MOCK_CONVERSATION_DTOS.find((c) => c.id === m.conversationId)?.advisorId === params.advisorId,
    )
    return HttpResponse.json(messages.slice(-limit))
  }),

  http.get('*/api/chats', () => HttpResponse.json(MOCK_CONVERSATION_DTOS)),
]
