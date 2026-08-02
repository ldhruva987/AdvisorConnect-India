import { useCallback, useEffect, useRef } from 'react'
import { useAuthStore } from '@/stores/authStore'
import { useChatStore } from '@/stores/chatStore'
import type { Message } from '@/types'

/**
 * Live chat WebSocket.
 *
 * Not a react-query hook: this is a long-lived bidirectional connection, not a
 * request. Incoming frames are pushed into `useChatStore` (the single source
 * of truth ChatPage renders from) rather than returned, so any component can
 * observe the conversation without owning the socket.
 */

/** First reconnect wait. Doubles per consecutive failure. */
const INITIAL_RECONNECT_DELAY_MS = 1_000
/** Ceiling on the backoff, so a long outage settles into steady retries. */
const MAX_RECONNECT_DELAY_MS = 30_000
/**
 * How long a typing indicator survives without a refreshing frame. The
 * protocol has no `TYPING_STOP`, so an indicator set from a `TYPING` frame
 * would otherwise stick forever once the advisor stopped typing.
 */
const TYPING_TIMEOUT_MS = 4_000

/**
 * `WebSocket.OPEN`. Inlined rather than read off the global so this module
 * doesn't depend on a test double replicating the class's static constants.
 */
const WS_OPEN = 1

/** Frames the server sends. Only `type` is guaranteed; the rest is defensive. */
interface IncomingFrame {
  type?: string
  id?: string
  conversationId?: string
  senderId?: string
  senderType?: string
  text?: string
  createdAt?: string
}

/**
 * Build the handshake URL from the current page origin.
 *
 * Derived from `window.location` rather than hardcoded: in development the
 * Vite dev server proxies `/ws` to the chat service, and in production the
 * app is served from the same origin as the gateway. Hardcoding `localhost`
 * would work in exactly one of those and fail silently in the other.
 *
 * The `token` param is required, not optional — backend Phase 7 adds a JWT
 * handshake interceptor and derives the caller's real identity from it.
 * Before that change the server trusted a raw `userId` query param, which let
 * anyone impersonate anyone; that param is deliberately not sent here.
 */
export function buildChatSocketUrl(
  advisorId: string,
  token: string,
  location: Pick<Location, 'protocol' | 'host'> = window.location,
): string {
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  const params = new URLSearchParams({ token, advisorId })
  return `${protocol}//${location.host}/ws/chat?${params.toString()}`
}

/**
 * Exponential backoff with full jitter: a random point in the upper half of
 * the current window. The jitter matters because every client of a service
 * that just restarted would otherwise reconnect in lockstep and knock it over
 * again.
 */
export function reconnectDelayMs(attempt: number, random: () => number = Math.random): number {
  const ceiling = Math.min(INITIAL_RECONNECT_DELAY_MS * 2 ** attempt, MAX_RECONNECT_DELAY_MS)
  return Math.round(ceiling / 2 + random() * (ceiling / 2))
}

/** Normalise a `MESSAGE` frame into the store's `Message` shape. */
function frameToMessage(frame: IncomingFrame, advisorId: string): Message {
  return {
    id: frame.id ?? `ws-${Date.now()}-${Math.round(Math.random() * 1e6)}`,
    // The protocol's minimal frame carries no conversation id; keying by
    // advisor is the same thing from this client's point of view, since a user
    // has at most one conversation per advisor.
    conversationId: frame.conversationId ?? advisorId,
    senderId: frame.senderId ?? advisorId,
    // Anything not explicitly marked as ours is treated as theirs — a frame we
    // can't classify should not render as if the user sent it.
    senderType: frame.senderType === 'user' ? 'user' : 'advisor',
    text: frame.text ?? '',
    createdAt: frame.createdAt ?? new Date().toISOString(),
  }
}

export interface UseChatSocketResult {
  /**
   * Send a `MESSAGE` frame. Returns `false` (and warns) rather than throwing
   * when the socket isn't open — a dropped connection is an expected state,
   * not an exception, and a send during reconnect shouldn't crash the page.
   */
  sendMessage: (text: string) => boolean
  isConnected: boolean
}

export function useChatSocket(advisorId: string | undefined): UseChatSocketResult {
  const socketRef = useRef<WebSocket | null>(null)
  const isConnected = useChatStore((s) => s.wsConnected)
  /**
   * Only used to decide *whether* to hold a connection. The access token
   * itself is read at connect time via `getState()` instead of being a
   * dependency, so a routine 15-minute token refresh doesn't tear down and
   * re-establish a healthy socket mid-conversation. A reconnect always picks
   * up whatever token is current.
   */
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)

  useEffect(() => {
    if (!advisorId || !isAuthenticated) return

    let disposed = false
    let attempt = 0
    let reconnectTimer: ReturnType<typeof setTimeout> | undefined
    let typingTimer: ReturnType<typeof setTimeout> | undefined

    const clearTypingTimer = () => {
      if (typingTimer !== undefined) {
        clearTimeout(typingTimer)
        typingTimer = undefined
      }
    }

    const handleFrame = (raw: unknown) => {
      if (typeof raw !== 'string') return

      let frame: IncomingFrame
      try {
        frame = JSON.parse(raw) as IncomingFrame
      } catch {
        console.warn('[useChatSocket] discarded unparseable frame')
        return
      }
      if (!frame || typeof frame !== 'object') return

      const chat = useChatStore.getState()

      if (frame.type === 'MESSAGE') {
        const message = frameToMessage(frame, advisorId)
        chat.appendMessage(message.conversationId, message)
        // A message implies they've stopped typing.
        clearTypingTimer()
        chat.setAdvisorTyping(advisorId, false)
        return
      }

      if (frame.type === 'TYPING') {
        chat.setAdvisorTyping(advisorId, true)
        clearTypingTimer()
        typingTimer = setTimeout(() => {
          useChatStore.getState().setAdvisorTyping(advisorId, false)
        }, TYPING_TIMEOUT_MS)
      }
    }

    const connect = () => {
      if (disposed) return

      const token = useAuthStore.getState().accessToken
      if (!token) return

      const socket = new WebSocket(buildChatSocketUrl(advisorId, token))
      socketRef.current = socket

      socket.onopen = () => {
        if (disposed) return
        attempt = 0
        useChatStore.getState().setWsConnected(true)
      }

      socket.onmessage = (event: MessageEvent) => {
        if (disposed) return
        handleFrame(event.data)
      }

      socket.onclose = () => {
        if (disposed) return
        useChatStore.getState().setWsConnected(false)
        reconnectTimer = setTimeout(connect, reconnectDelayMs(attempt))
        attempt += 1
      }

      // `error` is always followed by `close`, which owns the retry. Handling
      // it here too would double-schedule the reconnect.
      socket.onerror = () => {}
    }

    connect()

    return () => {
      disposed = true
      if (reconnectTimer !== undefined) clearTimeout(reconnectTimer)
      clearTypingTimer()

      const socket = socketRef.current
      socketRef.current = null
      if (socket) {
        // Detach before closing: otherwise our own `close()` fires `onclose`
        // and schedules a reconnect for a hook that no longer exists.
        socket.onopen = null
        socket.onmessage = null
        socket.onclose = null
        socket.onerror = null
        socket.close()
      }

      useChatStore.getState().setWsConnected(false)
      useChatStore.getState().setAdvisorTyping(advisorId, false)
    }
  }, [advisorId, isAuthenticated])

  const sendMessage = useCallback((text: string): boolean => {
    const socket = socketRef.current
    if (!socket || socket.readyState !== WS_OPEN) {
      console.warn('[useChatSocket] send ignored — socket is not open')
      return false
    }
    socket.send(JSON.stringify({ type: 'MESSAGE', text }))
    return true
  }, [])

  return { sendMessage, isConnected }
}
