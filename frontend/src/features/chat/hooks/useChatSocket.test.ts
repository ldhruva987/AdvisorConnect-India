import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '@/stores/authStore'
import { useChatStore } from '@/stores/chatStore'
import { act, loginAs, renderHook } from '@/test/test-utils'
import { buildChatSocketUrl, reconnectDelayMs, useChatSocket } from './useChatSocket'

/**
 * MSW does not intercept raw WebSockets, so this file installs its own double
 * on `globalThis.WebSocket`. It records every instance constructed, which is
 * how the reconnect assertions work: a reconnect is observable precisely as a
 * second construction.
 *
 * Store assertions read the real `useChatStore` rather than mocking it — the
 * contract under test is "a frame arrives and the store reflects it", and a
 * mocked store would only prove the hook called a function.
 */
class FakeWebSocket {
  static instances: FakeWebSocket[] = []
  static get last(): FakeWebSocket {
    return FakeWebSocket.instances[FakeWebSocket.instances.length - 1]
  }

  readyState = 0
  sent: string[] = []
  closeCount = 0
  url: string
  onopen: (() => void) | null = null
  onmessage: ((event: MessageEvent) => void) | null = null
  onclose: (() => void) | null = null
  onerror: (() => void) | null = null

  // Declared and assigned explicitly rather than as a constructor parameter
  // property: tsconfig sets `erasableSyntaxOnly`.
  constructor(url: string) {
    this.url = url
    FakeWebSocket.instances.push(this)
  }

  send(data: string) {
    this.sent.push(data)
  }

  close() {
    this.closeCount += 1
    this.readyState = 3
  }

  // ── test drivers ──────────────────────────────────────────────────────
  simulateOpen() {
    this.readyState = 1
    this.onopen?.()
  }

  simulateMessage(payload: unknown) {
    const data = typeof payload === 'string' ? payload : JSON.stringify(payload)
    this.onmessage?.({ data } as MessageEvent)
  }

  simulateClose() {
    this.readyState = 3
    this.onclose?.()
  }
}

beforeEach(() => {
  FakeWebSocket.instances = []
  vi.stubGlobal('WebSocket', FakeWebSocket)
  loginAs({ accessToken: 'jwt-token-123' })
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.useRealTimers()
})

describe('buildChatSocketUrl', () => {
  it('maps http to ws and carries token + advisorId', () => {
    const url = buildChatSocketUrl('advisor-1', 'tok', { protocol: 'http:', host: 'localhost:7100' })
    expect(url).toBe('ws://localhost:7100/ws/chat?token=tok&advisorId=advisor-1')
  })

  it('maps https to wss', () => {
    const url = buildChatSocketUrl('advisor-1', 'tok', {
      protocol: 'https:',
      host: 'advisorconnect.io',
    })
    expect(url.startsWith('wss://advisorconnect.io/ws/chat?')).toBe(true)
  })

  it('url-encodes a JWT containing url-unsafe characters', () => {
    const url = buildChatSocketUrl('advisor-1', 'a+b/c=', { protocol: 'http:', host: 'h' })
    expect(url).toContain('token=a%2Bb%2Fc%3D')
    expect(url).not.toContain('token=a+b/c=')
  })
})

describe('reconnectDelayMs', () => {
  it('grows exponentially from ~1s', () => {
    expect(reconnectDelayMs(0, () => 0)).toBe(500)
    expect(reconnectDelayMs(1, () => 0)).toBe(1000)
    expect(reconnectDelayMs(2, () => 0)).toBe(2000)
  })

  it('caps at 30s no matter how many attempts have failed', () => {
    expect(reconnectDelayMs(50, () => 1)).toBe(30_000)
    expect(reconnectDelayMs(50, () => 0)).toBe(15_000)
  })

  it('jitters within the upper half of the window', () => {
    // Never zero (a thundering herd would all retry instantly) and never
    // above the ceiling.
    for (const r of [0, 0.5, 0.999]) {
      const delay = reconnectDelayMs(3, () => r)
      expect(delay).toBeGreaterThanOrEqual(4000)
      expect(delay).toBeLessThanOrEqual(8000)
    }
  })
})

describe('useChatSocket', () => {
  it('connects to a URL carrying the advisorId and the JWT', () => {
    renderHook(() => useChatSocket('advisor-42'))

    expect(FakeWebSocket.instances).toHaveLength(1)
    const { url } = FakeWebSocket.last
    expect(url).toContain('/ws/chat?')
    expect(url).toContain('advisorId=advisor-42')
    expect(url).toContain('token=jwt-token-123')
    // Backend Phase 7 derives identity from the token; a client-supplied
    // userId is exactly the impersonation hole being closed.
    expect(url).not.toContain('userId=')
  })

  it('does not connect without an advisorId', () => {
    renderHook(() => useChatSocket(undefined))
    expect(FakeWebSocket.instances).toHaveLength(0)
  })

  it('does not connect when unauthenticated', () => {
    // `loginAs()` ran in beforeEach; undo it for this case.
    useAuthStore.getState().logout()

    renderHook(() => useChatSocket('advisor-1'))
    expect(FakeWebSocket.instances).toHaveLength(0)
  })

  it('flags the store connected on open and disconnected on close', () => {
    renderHook(() => useChatSocket('advisor-1'))

    expect(useChatStore.getState().wsConnected).toBe(false)

    act(() => FakeWebSocket.last.simulateOpen())
    expect(useChatStore.getState().wsConnected).toBe(true)

    act(() => FakeWebSocket.last.simulateClose())
    expect(useChatStore.getState().wsConnected).toBe(false)
  })

  it('appends an incoming MESSAGE frame to the real chat store', () => {
    renderHook(() => useChatSocket('advisor-1'))
    act(() => FakeWebSocket.last.simulateOpen())

    act(() =>
      FakeWebSocket.last.simulateMessage({
        type: 'MESSAGE',
        id: 'm-1',
        conversationId: 'conversation-1',
        senderId: 'advisor-1',
        senderType: 'advisor',
        text: 'Sounds good — talk Thursday.',
        createdAt: '2026-08-01T10:00:00Z',
      }),
    )

    const messages = useChatStore.getState().messages['conversation-1']
    expect(messages).toHaveLength(1)
    expect(messages[0]).toEqual({
      id: 'm-1',
      conversationId: 'conversation-1',
      senderId: 'advisor-1',
      senderType: 'advisor',
      text: 'Sounds good — talk Thursday.',
      createdAt: '2026-08-01T10:00:00Z',
    })
  })

  it('keys a minimal frame by advisorId and defaults the sender to the advisor', () => {
    // The documented minimal frame is just `{ type, text }`.
    renderHook(() => useChatSocket('advisor-9'))
    act(() => FakeWebSocket.last.simulateOpen())

    act(() => FakeWebSocket.last.simulateMessage({ type: 'MESSAGE', text: 'hi' }))

    const messages = useChatStore.getState().messages['advisor-9']
    expect(messages).toHaveLength(1)
    expect(messages[0].text).toBe('hi')
    expect(messages[0].senderType).toBe('advisor')
    expect(messages[0].id).toBeTruthy()
    expect(messages[0].createdAt).toBeTruthy()
  })

  it('marks the advisor typing on a TYPING frame, and clears it after a timeout', () => {
    vi.useFakeTimers()
    renderHook(() => useChatSocket('advisor-1'))
    act(() => FakeWebSocket.last.simulateOpen())

    act(() => FakeWebSocket.last.simulateMessage({ type: 'TYPING' }))
    expect(useChatStore.getState().typingAdvisors.has('advisor-1')).toBe(true)

    // No TYPING_STOP exists in the protocol, so the indicator must self-clear.
    act(() => void vi.advanceTimersByTime(5_000))
    expect(useChatStore.getState().typingAdvisors.has('advisor-1')).toBe(false)
  })

  it('clears the typing indicator as soon as the message itself arrives', () => {
    renderHook(() => useChatSocket('advisor-1'))
    act(() => FakeWebSocket.last.simulateOpen())

    act(() => FakeWebSocket.last.simulateMessage({ type: 'TYPING' }))
    expect(useChatStore.getState().typingAdvisors.has('advisor-1')).toBe(true)

    act(() => FakeWebSocket.last.simulateMessage({ type: 'MESSAGE', text: 'here it is' }))
    expect(useChatStore.getState().typingAdvisors.has('advisor-1')).toBe(false)
  })

  it('ignores unparseable and unknown frames without throwing', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    renderHook(() => useChatSocket('advisor-1'))
    act(() => FakeWebSocket.last.simulateOpen())

    act(() => FakeWebSocket.last.simulateMessage('not json at all'))
    act(() => FakeWebSocket.last.simulateMessage({ type: 'SOMETHING_NEW' }))

    expect(useChatStore.getState().messages).toEqual({})
    expect(warn).toHaveBeenCalled()
    warn.mockRestore()
  })

  it('schedules a reconnect after an unexpected close', () => {
    vi.useFakeTimers()
    renderHook(() => useChatSocket('advisor-1'))
    act(() => FakeWebSocket.last.simulateOpen())

    expect(FakeWebSocket.instances).toHaveLength(1)

    act(() => FakeWebSocket.last.simulateClose())
    // Not immediate — that would hammer a service that just restarted.
    expect(FakeWebSocket.instances).toHaveLength(1)

    act(() => void vi.advanceTimersByTime(30_000))
    expect(FakeWebSocket.instances).toHaveLength(2)
    expect(FakeWebSocket.last.url).toContain('advisorId=advisor-1')
  })

  it('backs off further on each consecutive failure', () => {
    vi.useFakeTimers()
    renderHook(() => useChatSocket('advisor-1'))

    act(() => FakeWebSocket.last.simulateClose())
    act(() => void vi.advanceTimersByTime(30_000))
    expect(FakeWebSocket.instances).toHaveLength(2)

    act(() => FakeWebSocket.last.simulateClose())
    // Second attempt's window starts at 1s, so 400ms cannot be enough.
    act(() => void vi.advanceTimersByTime(400))
    expect(FakeWebSocket.instances).toHaveLength(2)

    act(() => void vi.advanceTimersByTime(30_000))
    expect(FakeWebSocket.instances).toHaveLength(3)
  })

  it('tears down on unmount and never reconnects afterwards', () => {
    vi.useFakeTimers()
    const { unmount } = renderHook(() => useChatSocket('advisor-1'))
    act(() => FakeWebSocket.last.simulateOpen())

    const socket = FakeWebSocket.last
    unmount()

    expect(socket.closeCount).toBe(1)
    expect(useChatStore.getState().wsConnected).toBe(false)

    act(() => void vi.advanceTimersByTime(60_000))
    expect(FakeWebSocket.instances).toHaveLength(1)
  })

  it('does not reconnect when a pending retry is unmounted mid-backoff', () => {
    vi.useFakeTimers()
    const { unmount } = renderHook(() => useChatSocket('advisor-1'))

    act(() => FakeWebSocket.last.simulateClose())
    unmount()

    act(() => void vi.advanceTimersByTime(60_000))
    expect(FakeWebSocket.instances).toHaveLength(1)
  })

  it('reconnects to the new advisor when advisorId changes', () => {
    const { rerender } = renderHook(({ id }: { id: string }) => useChatSocket(id), {
      initialProps: { id: 'advisor-1' },
    })
    act(() => FakeWebSocket.last.simulateOpen())

    rerender({ id: 'advisor-2' })

    expect(FakeWebSocket.instances).toHaveLength(2)
    expect(FakeWebSocket.last.url).toContain('advisorId=advisor-2')
  })

  it('sends a MESSAGE frame when open', () => {
    const { result } = renderHook(() => useChatSocket('advisor-1'))
    act(() => FakeWebSocket.last.simulateOpen())

    let sent = false
    act(() => {
      sent = result.current.sendMessage('hello there')
    })

    expect(sent).toBe(true)
    expect(FakeWebSocket.last.sent).toEqual([JSON.stringify({ type: 'MESSAGE', text: 'hello there' })])
  })

  it('warns and returns false — never throws — when the socket is not open', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    const { result } = renderHook(() => useChatSocket('advisor-1'))

    // Constructed but never opened.
    let sent = true
    act(() => {
      sent = result.current.sendMessage('too early')
    })

    expect(sent).toBe(false)
    expect(FakeWebSocket.last.sent).toEqual([])
    expect(warn).toHaveBeenCalled()
    warn.mockRestore()
  })

  it('exposes the connection flag from the store', () => {
    const { result } = renderHook(() => useChatSocket('advisor-1'))
    expect(result.current.isConnected).toBe(false)

    act(() => FakeWebSocket.last.simulateOpen())
    expect(result.current.isConnected).toBe(true)
  })
})
