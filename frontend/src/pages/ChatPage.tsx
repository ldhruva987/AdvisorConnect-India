import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { MessagesSquare, Search, Send, User, Video } from 'lucide-react'
import { Avatar } from '@/shared/components/ui/Avatar'
import { Button } from '@/shared/components/ui/Button'
import { EmptyState } from '@/shared/components/ui/EmptyState'
import { ErrorBanner } from '@/shared/components/ui/ErrorBanner'
import { Skeleton } from '@/shared/components/ui/Skeleton'
import { Navbar } from '@/shared/components/layout/Navbar'
import { useChatSocket } from '@/features/chat/hooks/useChatSocket'
import { useConversations } from '@/features/chat/hooks/useConversations'
import { useMessageHistory } from '@/features/chat/hooks/useMessageHistory'
import { formatConversationTime } from '@/lib/formatDateTime'
import { getErrorMessage } from '@/lib/getErrorMessage'
import { useChatStore } from '@/stores/chatStore'
import type { Message } from '@/types'

/**
 * Stable identity for "this thread has no messages". Returning a fresh `[]`
 * from the zustand selector would give a new reference on every store change
 * and re-render the whole thread for unrelated updates.
 */
const NO_MESSAGES: Message[] = []

/**
 * Server timestamps are ISO instants (`2026-07-31T18:04:00Z`). Rendered as a
 * local wall-clock time. Anything unparseable is shown verbatim rather than as
 * "Invalid Date" — a malformed timestamp should not disfigure the message.
 */
function formatMessageTime(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return iso
  return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}

function ConversationSkeleton() {
  return (
    <div className="flex items-start gap-3 p-4" role="status" aria-label="Loading conversations">
      <Skeleton className="w-10 h-10 rounded-full flex-shrink-0" />
      <div className="flex-1 space-y-2 pt-1">
        <Skeleton className="h-3 w-2/5" />
        <Skeleton className="h-3 w-4/5" />
      </div>
    </div>
  )
}

function MessageSkeleton() {
  return (
    <div className="space-y-4" role="status" aria-label="Loading messages">
      {[
        { mine: false, width: 'w-3/5' },
        { mine: true, width: 'w-2/5' },
        { mine: false, width: 'w-1/2' },
      ].map((row, index) => (
        <div key={index} className={`flex ${row.mine ? 'justify-end' : 'justify-start'}`}>
          <Skeleton className={`h-14 rounded-xl ${row.width}`} />
        </div>
      ))}
    </div>
  )
}

export function ChatPage() {
  const navigate = useNavigate()
  /**
   * The page is mounted at both `/chat` and `/chat/:advisorId`. Reading the
   * param is what makes the second route mean anything: before this, every URL
   * rendered one hardcoded thread.
   */
  const { advisorId } = useParams<{ advisorId?: string }>()

  const [newMessage, setNewMessage] = useState('')
  const [searchQuery, setSearchQuery] = useState('')
  const [sendError, setSendError] = useState<string | null>(null)
  const messagesEndRef = useRef<HTMLDivElement>(null)

  // ── Store (the single source of truth for what's on screen) ──────────────
  const conversations = useChatStore((s) => s.conversations)
  const wsConnected = useChatStore((s) => s.wsConnected)
  const setConversations = useChatStore((s) => s.setConversations)
  const setMessages = useChatStore((s) => s.setMessages)
  const setActiveConversation = useChatStore((s) => s.setActiveConversation)
  const markAsRead = useChatStore((s) => s.markAsRead)

  // ── Remote data ──────────────────────────────────────────────────────────
  const conversationsQuery = useConversations()
  const historyQuery = useMessageHistory(advisorId)

  useEffect(() => {
    if (conversationsQuery.data) setConversations(conversationsQuery.data)
  }, [conversationsQuery.data, setConversations])

  /**
   * The conversation for the advisor in the URL, if the server already knows
   * about one.
   */
  const activeConversation = useMemo(
    () => (advisorId ? (conversations.find((c) => c.advisorId === advisorId) ?? null) : null),
    [conversations, advisorId],
  )

  /**
   * Key the thread is stored and rendered under.
   *
   * Falls back to the raw `advisorId` so a brand-new chat has somewhere to
   * live: the backend only materialises a conversation once a message exists,
   * so blocking on one being present server-side would make it impossible to
   * ever send the first message. `useChatSocket` keys unattributed frames by
   * advisorId for exactly the same reason, so the two agree.
   */
  const threadId = activeConversation?.id ?? advisorId ?? null

  const messages = useChatStore((s) => (threadId ? (s.messages[threadId] ?? NO_MESSAGES) : NO_MESSAGES))
  const advisorTyping = useChatStore((s) => (advisorId ? s.typingAdvisors.has(advisorId) : false))

  useEffect(() => {
    setActiveConversation(threadId)
  }, [threadId, setActiveConversation])

  useEffect(() => {
    const history = historyQuery.data
    if (!history || !threadId) return
    /**
     * Server messages carry their own `conversationId`. Trusting it matters
     * during the first paint of a deep link: `threadId` is still the advisor
     * id until the conversation list resolves, and writing history under that
     * temporary key would strand it once the real id arrives.
     */
    setMessages(history[0]?.conversationId ?? threadId, history)
  }, [historyQuery.data, threadId, setMessages])

  /** Opening a thread clears its badge. */
  useEffect(() => {
    if (activeConversation && activeConversation.unreadCount > 0) {
      markAsRead(activeConversation.id)
    }
  }, [activeConversation, markAsRead])

  // ── Live socket ──────────────────────────────────────────────────────────
  // Incoming frames are dispatched straight into the store by the hook; there
  // is nothing to wire up here beyond holding the connection open.
  const { sendMessage } = useChatSocket(advisorId)

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, advisorTyping])

  const handleSend = () => {
    const text = newMessage.trim()
    if (!text) return

    /**
     * Echo-only: the server broadcasts the message back to its sender, and
     * `useChatSocket` appends that frame to the store. No optimistic append
     * here because `chatStore.appendMessage` is an unconditional push with no
     * de-duplication — an optimistic bubble would be joined by a second copy
     * the moment the echo landed.
     */
    if (!sendMessage(text)) {
      setSendError('Not connected — your message was not sent. Retry once the connection is back.')
      return
    }
    setSendError(null)
    setNewMessage('')
  }

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSend()
    }
  }

  const filteredConvs = useMemo(() => {
    const q = searchQuery.trim().toLowerCase()
    if (!q) return conversations
    return conversations.filter((c) => c.advisorUsername.toLowerCase().includes(q))
  }, [conversations, searchQuery])

  const conversationsError = conversationsQuery.error
    ? getErrorMessage(conversationsQuery.error)
    : null
  const historyError = historyQuery.error ? getErrorMessage(historyQuery.error) : null

  return (
    <div className="fixed top-0 w-full pt-16 flex h-screen bg-ink-50">
      <Navbar />

      {/* Sidebar */}
      <aside className="w-72 bg-white border-r border-ink-200 hidden md:flex flex-col flex-shrink-0">
        <div className="p-4 border-b border-ink-100">
          <h2 className="font-heading font-semibold text-ink-900 mb-3">My Conversations</h2>
          <div className="relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-ink-400" />
            <input
              type="text"
              placeholder="Search..."
              aria-label="Search conversations"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full pl-9 pr-3 py-2 text-sm border border-ink-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-oxblood-700 bg-ink-50"
            />
          </div>
        </div>

        <div className="flex-1 overflow-y-auto">
          {conversationsQuery.isLoading ? (
            <>
              <ConversationSkeleton />
              <ConversationSkeleton />
              <ConversationSkeleton />
            </>
          ) : conversationsError ? (
            <div className="p-4">
              <ErrorBanner message={conversationsError} onRetry={() => void conversationsQuery.refetch()} />
            </div>
          ) : filteredConvs.length === 0 ? (
            <EmptyState
              className="py-10"
              title="No conversations"
              description={
                searchQuery.trim() ? 'No advisor matches that search.' : 'Message an advisor to start one.'
              }
            />
          ) : (
            filteredConvs.map((conv) => (
              <button
                key={conv.id}
                onClick={() => {
                  markAsRead(conv.id)
                  // The URL owns which thread is open, so selecting one
                  // navigates rather than setting local state. That keeps the
                  // socket, the history fetch and the address bar in agreement.
                  navigate(`/chat/${conv.advisorId}`)
                }}
                className={`w-full flex items-start gap-3 p-4 hover:bg-ink-50 transition-colors text-left ${
                  activeConversation?.id === conv.id ? 'bg-oxblood-50' : ''
                }`}
              >
                <Avatar
                  username={conv.advisorUsername}
                  color={conv.advisorColor}
                  size="md"
                  showOnline={conv.isAdvisorOnline}
                />
                <div className="flex-1 min-w-0">
                  <div className="flex items-center justify-between mb-0.5">
                    <span className="text-sm font-semibold text-ink-900 truncate">
                      {conv.advisorUsername}
                    </span>
                    <span className="text-xs text-ink-400 flex-shrink-0 ml-1">
                      {formatConversationTime(conv.lastMessageAt)}
                    </span>
                  </div>
                  <p className="text-xs text-ink-500 truncate">{conv.lastMessage}</p>
                </div>
                {conv.unreadCount > 0 && (
                  <span
                    aria-label={`${conv.unreadCount} unread messages`}
                    className="w-5 h-5 rounded-full bg-oxblood-700 text-white text-xs font-bold flex items-center justify-center flex-shrink-0 mt-0.5"
                  >
                    {conv.unreadCount}
                  </span>
                )}
              </button>
            ))
          )}
        </div>
      </aside>

      {/* Main pane */}
      <main className="flex-1 flex flex-col bg-ink-50 h-[calc(100vh-64px)]">
        {advisorId ? (
          <>
            {/* Chat header */}
            <div className="bg-white border-b border-ink-200 px-5 py-3 flex items-center gap-3">
              <Avatar
                username={activeConversation?.advisorUsername ?? advisorId}
                color={activeConversation?.advisorColor}
                size="md"
                showOnline={activeConversation?.isAdvisorOnline ?? false}
              />
              <div className="flex-1">
                <h3 className="font-semibold text-ink-900 text-sm">
                  {activeConversation?.advisorUsername ?? 'New conversation'}
                </h3>
                <p className="text-xs text-ink-400">
                  {activeConversation
                    ? activeConversation.isAdvisorOnline
                      ? '🟢 Online · Responds in <5 min'
                      : '⚫ Offline'
                    : 'Send a message to start this chat'}
                </p>
              </div>
              <div className="flex items-center gap-2">
                {/* Non-blocking: the thread stays readable and the socket is
                    already retrying underneath. */}
                {!wsConnected && (
                  <span
                    role="status"
                    className="text-xs font-medium text-warn-600 bg-warn-100 border border-warn-500/30 rounded-full px-3 py-1"
                  >
                    Reconnecting…
                  </span>
                )}
                {activeConversation && (
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => navigate(`/advisor/${activeConversation.advisorUsername}`)}
                  >
                    <User className="w-4 h-4" />
                    View Profile
                  </Button>
                )}
                <Button variant="primary" size="sm" onClick={() => navigate(`/book/${advisorId}`)}>
                  <Video className="w-4 h-4" />
                  Book Video
                </Button>
              </div>
            </div>

            {/* Messages */}
            <div className="flex-1 overflow-y-auto p-5 space-y-4">
              <div className="flex justify-center">
                <span className="bg-pine-100 text-pine-600 text-xs font-semibold px-4 py-1.5 rounded-full">
                  ✓ Free chat — unlimited messages
                </span>
              </div>

              {historyError && <ErrorBanner message={historyError} onRetry={() => void historyQuery.refetch()} />}

              {historyQuery.isLoading ? (
                <MessageSkeleton />
              ) : (
                messages.map((msg) => (
                  <div
                    key={msg.id}
                    className={`flex ${
                      msg.senderType === 'user' ? 'justify-end' : 'justify-start items-end gap-2'
                    }`}
                  >
                    {msg.senderType === 'advisor' && (
                      <Avatar
                        username={activeConversation?.advisorUsername ?? advisorId}
                        color={activeConversation?.advisorColor}
                        size="sm"
                      />
                    )}
                    <div className="max-w-[70%]">
                      <div
                        className={`px-4 py-3 text-sm leading-relaxed ${
                          msg.senderType === 'user' ? 'msg-bubble-user' : 'msg-bubble-advisor'
                        }`}
                      >
                        {msg.text}
                      </div>
                      <p
                        className={`text-xs text-ink-400 mt-1 ${
                          msg.senderType === 'user' ? 'text-right' : ''
                        }`}
                      >
                        {formatMessageTime(msg.createdAt)}
                      </p>
                    </div>
                  </div>
                ))
              )}

              {/* Typing indicator — driven only by a real inbound TYPING frame. */}
              {advisorTyping && (
                <div className="flex items-end gap-2" role="status" aria-label="Advisor is typing">
                  <Avatar
                    username={activeConversation?.advisorUsername ?? advisorId}
                    color={activeConversation?.advisorColor}
                    size="sm"
                  />
                  <div className="msg-bubble-advisor px-4 py-3">
                    <div className="flex gap-1 items-center h-4">
                      {[0, 1, 2].map((i) => (
                        <span
                          key={i}
                          className="w-2 h-2 rounded-full bg-ink-400 animate-bounce"
                          style={{ animationDelay: `${i * 0.15}s` }}
                        />
                      ))}
                    </div>
                  </div>
                </div>
              )}

              <div ref={messagesEndRef} />
            </div>

            {/* Input bar */}
            <div className="bg-white border-t border-ink-200 p-4">
              {sendError && <ErrorBanner className="mb-3" message={sendError} />}
              <div className="flex items-end gap-3">
                <textarea
                  value={newMessage}
                  onChange={(e) => setNewMessage(e.target.value)}
                  onKeyDown={handleKeyDown}
                  placeholder="Type a message..."
                  aria-label="Message"
                  rows={1}
                  className="flex-1 input-base resize-none max-h-32 py-2.5 text-sm"
                  style={{ minHeight: '44px' }}
                />
                <button
                  onClick={handleSend}
                  disabled={!newMessage.trim()}
                  className="w-11 h-11 bg-oxblood-600 hover:bg-oxblood-700 disabled:opacity-40 rounded-lg flex items-center justify-center transition-colors flex-shrink-0"
                  aria-label="Send message"
                >
                  <Send className="w-4 h-4 text-white" />
                </button>
              </div>
              <p className="text-xs text-ink-400 mt-2 text-center">
                Chat is always free. Book a video session for deeper consultation.
              </p>
            </div>
          </>
        ) : (
          <div className="flex-1 flex items-center justify-center">
            <EmptyState
              icon={<MessagesSquare className="w-10 h-10" />}
              title="Select a conversation"
              description="Choose a chat from the list, or message an advisor from their profile."
            />
          </div>
        )}
      </main>
    </div>
  )
}
