import { create } from 'zustand'
import type { Message, Conversation } from '@/types'

interface ChatStore {
  conversations: Conversation[]
  messages: Record<string, Message[]>          // key: conversationId
  activeConversationId: string | null
  typingAdvisors: Set<string>                  // advisorIds currently typing
  wsConnected: boolean
  // actions
  setConversations: (convs: Conversation[]) => void
  setMessages: (convId: string, msgs: Message[]) => void
  appendMessage: (convId: string, msg: Message) => void
  setActiveConversation: (id: string | null) => void
  setAdvisorTyping: (advisorId: string, isTyping: boolean) => void
  markAsRead: (convId: string) => void
  setWsConnected: (v: boolean) => void
}

export const useChatStore = create<ChatStore>((set) => ({
  conversations: [],
  messages: {},
  activeConversationId: null,
  typingAdvisors: new Set(),
  wsConnected: false,

  setConversations: (conversations) => set({ conversations }),

  setMessages: (convId, msgs) =>
    set((s) => ({ messages: { ...s.messages, [convId]: msgs } })),

  appendMessage: (convId, msg) =>
    set((s) => ({
      messages: { ...s.messages, [convId]: [...(s.messages[convId] ?? []), msg] },
    })),

  setActiveConversation: (activeConversationId) => set({ activeConversationId }),

  setAdvisorTyping: (advisorId, isTyping) =>
    set((s) => {
      const next = new Set(s.typingAdvisors)
      isTyping ? next.add(advisorId) : next.delete(advisorId)
      return { typingAdvisors: next }
    }),

  markAsRead: (convId) =>
    set((s) => ({
      conversations: s.conversations.map((c) =>
        c.id === convId ? { ...c, unreadCount: 0 } : c,
      ),
    })),

  setWsConnected: (wsConnected) => set({ wsConnected }),
}))
