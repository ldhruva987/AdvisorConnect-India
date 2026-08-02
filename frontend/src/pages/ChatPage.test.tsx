import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import userEvent from '@testing-library/user-event'
import { Route, Routes, useLocation } from 'react-router-dom'
import { server } from '@/test/mocks/server'
import { MOCK_CONVERSATION_DTOS } from '@/test/mocks/handlers/chat'
import { useChatStore } from '@/stores/chatStore'
import { act, fireEvent, loginAs, render, screen, waitFor, within } from '@/test/test-utils'
import { ChatPage } from './ChatPage'

/**
 * ChatPage is the page-level counterpart to `useChatSocket.test.ts`: that file
 * proves the socket hook maintains the store, this one proves the page is
 * actually wired to the store, to the two REST queries, and to the socket —
 * rather than to the hardcoded fixtures it used to ship with.
 *
 * MSW cannot intercept raw WebSockets, so the same `FakeWebSocket` double the
 * hook test uses is installed here. It is deliberately a copy rather than a
 * shared export: the hook test's double is part of that test's own statement of
 * the protocol, and coupling the two would mean a change to one silently
 * rewrites the other's meaning.
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

  // Assigned in the body rather than as a parameter property: tsconfig sets
  // `erasableSyntaxOnly`.
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

/**
 * The canned advisor lines the old page replayed on a 1.5s `setTimeout` after
 * every send. Kept here verbatim so the regression test asserts against the
 * real thing rather than a paraphrase of it.
 */
const OLD_CANNED_REPLIES = [
  "That's a really insightful question. Let me share some techniques that have worked well for my clients...",
  "I understand where you're coming from. This is something many people struggle with, and it's completely normal.",
  "Based on what you've shared, I think a CBT approach could be really helpful here. Would you be open to trying a small exercise?",
  'Great question! The research actually shows that even 10 minutes of mindfulness per day can make a significant difference.',
  "I'm glad you reached out. Let's take this step by step and figure out the best path forward for you.",
]

/** The hardcoded sidebar the old page rendered for every user alike. */
const OLD_MOCK_USERNAMES = ['@MindfulRohan', '@SarahCareerPro', '@FinanceWithTed']

/** Exposes the current pathname so sidebar navigation is assertable. */
function LocationProbe() {
  const { pathname } = useLocation()
  return <span data-testid="pathname">{pathname}</span>
}

/**
 * Mounts the page under a real route pattern. `path` matters: the page reads
 * `useParams()`, and without a matching route React Router hands back empty
 * params — which is indistinguishable from the bug being tested for.
 */
function renderChat(advisorId?: string) {
  return advisorId
    ? render(<ChatPage />, { initialEntries: [`/chat/${advisorId}`], path: '/chat/:advisorId' })
    : render(<ChatPage />, { initialEntries: ['/chat'], path: '/chat' })
}

/** Same mount, but with a probe that survives navigating between threads. */
function renderChatForNavigation(advisorId: string) {
  return render(
    <>
      <LocationProbe />
      <Routes>
        <Route path="/chat" element={<ChatPage />} />
        <Route path="/chat/:advisorId" element={<ChatPage />} />
        <Route path="*" element={null} />
      </Routes>
    </>,
    { initialEntries: [`/chat/${advisorId}`] },
  )
}

/** Resolves once the conversation list has landed in the sidebar. */
async function waitForSidebar() {
  await screen.findByRole('button', { name: /maya_chen/ })
}

beforeEach(() => {
  FakeWebSocket.instances = []
  vi.stubGlobal('WebSocket', FakeWebSocket)
  // jsdom has no layout engine and ships no `scrollIntoView`; the page calls it
  // on every message change.
  Element.prototype.scrollIntoView = vi.fn()
  // Without a token `useChatSocket` refuses to connect, and every socket
  // assertion below would pass vacuously.
  loginAs({ userId: 'user-1', accessToken: 'jwt-token-123' })
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.useRealTimers()
})

describe('ChatPage', () => {
  describe('conversation list', () => {
    it('populates the chat store from the conversations API on mount', async () => {
      renderChat()

      await waitFor(() =>
        expect(useChatStore.getState().conversations.map((c) => c.id)).toEqual([
          'conversation-1',
          'conversation-2',
        ]),
      )
    })

    it('renders sidebar rows from the API, not a hardcoded list', async () => {
      renderChat()
      await waitForSidebar()

      expect(screen.getByText('sam_okafor')).toBeInTheDocument()
      expect(screen.getByText('Try the breathing exercise tonight.')).toBeInTheDocument()
      for (const stale of OLD_MOCK_USERNAMES) {
        expect(screen.queryByText(stale)).not.toBeInTheDocument()
      }
    })

    it('shows the unread badge the API reported', async () => {
      renderChat()
      await waitForSidebar()

      expect(screen.getByLabelText('2 unread messages')).toBeInTheDocument()
    })

    it('clears the unread badge for the thread the URL has open', async () => {
      renderChat('advisor-1')
      await waitForSidebar()

      await waitFor(() => expect(screen.queryByLabelText('2 unread messages')).not.toBeInTheDocument())
      expect(useChatStore.getState().conversations[0].unreadCount).toBe(0)
    })

    it('filters the list by advisor username', async () => {
      const user = userEvent.setup()
      renderChat()
      await waitForSidebar()

      await user.type(screen.getByLabelText('Search conversations'), 'sam')

      expect(screen.getByText('sam_okafor')).toBeInTheDocument()
      expect(screen.queryByText('maya_chen')).not.toBeInTheDocument()
    })

    it('shows placeholder rows while the list loads', () => {
      renderChat()
      expect(screen.getAllByRole('status', { name: /loading conversations/i })).toHaveLength(3)
    })

    it('shows an ErrorBanner with a working retry when the list fails', async () => {
      let attempt = 0
      server.use(
        http.get('*/api/chats', () => {
          attempt += 1
          if (attempt === 1) return new HttpResponse(null, { status: 500 })
          return HttpResponse.json(MOCK_CONVERSATION_DTOS)
        }),
      )
      const user = userEvent.setup()
      renderChat()

      const banner = await screen.findByRole('alert')
      await user.click(within(banner).getByRole('button', { name: /try again/i }))

      await waitForSidebar()
      expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    })

    it('navigates to the advisor of the row that was clicked', async () => {
      const user = userEvent.setup()
      renderChatForNavigation('advisor-1')
      await waitForSidebar()

      await user.click(screen.getByRole('button', { name: /sam_okafor/ }))

      expect(screen.getByTestId('pathname')).toHaveTextContent('/chat/advisor-2')
    })
  })

  describe('reading the advisor from the URL', () => {
    /**
     * The bug this covers: the page never called `useParams()`, so `/chat/x`
     * and `/chat/y` both rendered one fixed thread.
     */
    it('requests history for the advisor in the URL, not a fixed one', async () => {
      const requested: string[] = []
      server.use(
        http.get('*/api/chats/:advisorId/messages', ({ params }) => {
          requested.push(String(params.advisorId))
          return HttpResponse.json([])
        }),
      )
      renderChat('advisor-2')
      await waitForSidebar()

      await waitFor(() => expect(requested).toEqual(['advisor-2']))
      // The `enabled` guard must keep a literal `undefined` off the wire.
      expect(requested).not.toContain('undefined')
    })

    it('opens the thread named in the URL, not the first conversation', async () => {
      renderChat('advisor-2')

      expect(await screen.findByRole('heading', { name: 'sam_okafor', level: 3 })).toBeInTheDocument()
      expect(useChatStore.getState().activeConversationId).toBe('conversation-2')
    })

    it('renders an empty state at bare /chat', async () => {
      renderChat()
      await waitForSidebar()

      expect(screen.getByText('Select a conversation')).toBeInTheDocument()
      expect(screen.queryByLabelText('Message')).not.toBeInTheDocument()
      // No socket without an advisor to open one for.
      expect(FakeWebSocket.instances).toHaveLength(0)
    })
  })

  describe('message history', () => {
    it('loads history into the store and renders it in the thread', async () => {
      renderChat('advisor-1')

      expect(await screen.findByText('Is Thursday still okay?')).toBeInTheDocument()
      const store = useChatStore.getState()
      expect(store.messages['conversation-1'].map((m) => m.id)).toEqual(['message-1', 'message-2'])
    })

    /**
     * On the first paint of a deep link the conversation list has not resolved,
     * so the page's thread key is still the raw advisorId. History must be
     * stored under the id the *server* attached, or it is stranded the moment
     * the real conversation id arrives.
     */
    it('keys history by the conversation id the server returned', async () => {
      renderChat('advisor-1')
      await screen.findByText('Is Thursday still okay?')

      expect(useChatStore.getState().messages['advisor-1']).toBeUndefined()
      expect(useChatStore.getState().messages['conversation-1']).toHaveLength(2)
    })

    it('shows a skeleton while history loads', () => {
      renderChat('advisor-1')
      expect(screen.getByRole('status', { name: /loading messages/i })).toBeInTheDocument()
    })

    it('shows an ErrorBanner when history fails but keeps the thread usable', async () => {
      server.use(
        http.get('*/api/chats/:advisorId/messages', () => new HttpResponse(null, { status: 500 })),
      )
      renderChat('advisor-1')

      expect(await screen.findByRole('alert')).toHaveTextContent(
        'Something went wrong. Please try again.',
      )
      expect(screen.getByLabelText('Message')).toBeInTheDocument()
    })
  })

  describe('sending over the socket', () => {
    it('opens a socket for the advisor in the URL', async () => {
      renderChat('advisor-1')
      await waitForSidebar()

      expect(FakeWebSocket.instances).toHaveLength(1)
      expect(FakeWebSocket.last.url).toContain('advisorId=advisor-1')
    })

    it('sends the typed text as a MESSAGE frame and clears the composer', async () => {
      const user = userEvent.setup()
      renderChat('advisor-1')
      await screen.findByText('Is Thursday still okay?')
      act(() => FakeWebSocket.last.simulateOpen())

      const composer = screen.getByLabelText('Message')
      await user.type(composer, 'Can we move to Friday?')
      await user.click(screen.getByRole('button', { name: /send message/i }))

      expect(FakeWebSocket.last.sent).toEqual([
        JSON.stringify({ type: 'MESSAGE', text: 'Can we move to Friday?' }),
      ])
      expect(composer).toHaveValue('')
    })

    it('sends on Enter and inserts a newline on Shift+Enter', async () => {
      const user = userEvent.setup()
      renderChat('advisor-1')
      await screen.findByText('Is Thursday still okay?')
      act(() => FakeWebSocket.last.simulateOpen())

      const composer = screen.getByLabelText('Message')
      await user.type(composer, 'line one{Shift>}{Enter}{/Shift}line two')
      expect(FakeWebSocket.last.sent).toEqual([])
      expect(composer).toHaveValue('line one\nline two')

      await user.type(composer, '{Enter}')
      expect(FakeWebSocket.last.sent).toEqual([
        JSON.stringify({ type: 'MESSAGE', text: 'line one\nline two' }),
      ])
    })

    it('trims the outgoing text and refuses a whitespace-only send', async () => {
      const user = userEvent.setup()
      renderChat('advisor-1')
      await screen.findByText('Is Thursday still okay?')
      act(() => FakeWebSocket.last.simulateOpen())

      const composer = screen.getByLabelText('Message')
      await user.type(composer, '   ')
      expect(screen.getByRole('button', { name: /send message/i })).toBeDisabled()

      await user.type(composer, ' hello  ')
      await user.click(screen.getByRole('button', { name: /send message/i }))

      expect(FakeWebSocket.last.sent).toEqual([JSON.stringify({ type: 'MESSAGE', text: 'hello' })])
    })

    it('reports the failure and keeps the draft when the socket is closed', async () => {
      const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
      const user = userEvent.setup()
      renderChat('advisor-1')
      await screen.findByText('Is Thursday still okay?')
      // Deliberately never opened.

      const composer = screen.getByLabelText('Message')
      await user.type(composer, 'anyone there?')
      await user.click(screen.getByRole('button', { name: /send message/i }))

      expect(await screen.findByRole('alert')).toHaveTextContent(/not connected/i)
      // Losing an unsent draft is the worst possible response to a dropped
      // connection, so it must survive.
      expect(composer).toHaveValue('anyone there?')
      expect(FakeWebSocket.last.sent).toEqual([])
      warn.mockRestore()
    })

    it('shows a reconnecting pill while the socket is down', async () => {
      renderChat('advisor-1')
      await waitForSidebar()

      expect(screen.getByText(/reconnecting/i)).toBeInTheDocument()

      act(() => FakeWebSocket.last.simulateOpen())
      expect(screen.queryByText(/reconnecting/i)).not.toBeInTheDocument()

      act(() => FakeWebSocket.last.simulateClose())
      expect(screen.getByText(/reconnecting/i)).toBeInTheDocument()
    })
  })

  describe('receiving over the socket', () => {
    it('appends an inbound MESSAGE frame to the open thread', async () => {
      renderChat('advisor-1')
      await screen.findByText('Is Thursday still okay?')
      act(() => FakeWebSocket.last.simulateOpen())

      act(() =>
        FakeWebSocket.last.simulateMessage({
          type: 'MESSAGE',
          id: 'message-3',
          conversationId: 'conversation-1',
          senderId: 'advisor-1',
          senderType: 'advisor',
          text: 'Friday works — 3pm?',
          createdAt: '2026-08-01T09:15:00Z',
        }),
      )

      expect(await screen.findByText('Friday works — 3pm?')).toBeInTheDocument()
      expect(useChatStore.getState().messages['conversation-1']).toHaveLength(3)
    })

    it('renders the typing indicator only from a real TYPING frame', async () => {
      renderChat('advisor-1')
      await screen.findByText('Is Thursday still okay?')
      act(() => FakeWebSocket.last.simulateOpen())

      expect(screen.queryByRole('status', { name: /advisor is typing/i })).not.toBeInTheDocument()

      act(() => FakeWebSocket.last.simulateMessage({ type: 'TYPING' }))
      expect(screen.getByRole('status', { name: /advisor is typing/i })).toBeInTheDocument()

      act(() => FakeWebSocket.last.simulateMessage({ type: 'MESSAGE', text: 'here it is' }))
      expect(screen.queryByRole('status', { name: /advisor is typing/i })).not.toBeInTheDocument()
    })
  })

  describe('regression: the simulated advisor is gone', () => {
    /**
     * The old page answered every send itself: it set a local `advisorTyping`
     * flag, waited 1.5s on a `setTimeout`, then pushed the next line of a
     * five-entry `ADVISOR_REPLIES` array into the thread. Users were talking to
     * a carousel.
     *
     * `fireEvent` rather than `userEvent` here because fake timers are
     * installed mid-test and `userEvent`'s own scheduling would otherwise
     * deadlock against them.
     */
    it('produces no reply, and no typing indicator, without an inbound frame', async () => {
      renderChat('advisor-1')
      await screen.findByText('Is Thursday still okay?')
      await waitForSidebar()
      act(() => FakeWebSocket.last.simulateOpen())

      // Only `setTimeout` is faked, so MSW, react-query and React's scheduler
      // keep their real clocks.
      vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })

      const composer = screen.getByLabelText('Message')
      fireEvent.change(composer, { target: { value: 'Are you there?' } })
      fireEvent.click(screen.getByRole('button', { name: /send message/i }))

      expect(FakeWebSocket.last.sent).toEqual([
        JSON.stringify({ type: 'MESSAGE', text: 'Are you there?' }),
      ])

      // Far longer than the old 1.5s reply delay.
      act(() => void vi.advanceTimersByTime(60_000))

      expect(useChatStore.getState().messages['conversation-1']).toHaveLength(2)
      expect(screen.queryByRole('status', { name: /advisor is typing/i })).not.toBeInTheDocument()
      const text = document.body.textContent ?? ''
      for (const reply of OLD_CANNED_REPLIES) {
        expect(text).not.toContain(reply)
      }
    })

    it('still renders a reply that genuinely arrives after the same delay', async () => {
      renderChat('advisor-1')
      await screen.findByText('Is Thursday still okay?')
      await waitForSidebar()
      act(() => FakeWebSocket.last.simulateOpen())

      vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })

      fireEvent.change(screen.getByLabelText('Message'), { target: { value: 'Are you there?' } })
      fireEvent.click(screen.getByRole('button', { name: /send message/i }))
      act(() => void vi.advanceTimersByTime(60_000))

      // The negative test above is only meaningful if the positive one passes:
      // a page that rendered nothing at all would also have no canned reply.
      act(() =>
        FakeWebSocket.last.simulateMessage({
          type: 'MESSAGE',
          conversationId: 'conversation-1',
          text: 'Yes — sorry, was on a call.',
        }),
      )

      expect(screen.getByText('Yes — sorry, was on a call.')).toBeInTheDocument()
      expect(useChatStore.getState().messages['conversation-1']).toHaveLength(3)
    })

    it('does not echo the sent message locally — the server round-trip owns it', async () => {
      const user = userEvent.setup()
      renderChat('advisor-1')
      await screen.findByText('Is Thursday still okay?')
      act(() => FakeWebSocket.last.simulateOpen())

      await user.type(screen.getByLabelText('Message'), 'ping')
      await user.click(screen.getByRole('button', { name: /send message/i }))

      // `chatStore.appendMessage` does not de-duplicate, so an optimistic
      // bubble here would be joined by a second copy when the echo landed.
      expect(useChatStore.getState().messages['conversation-1']).toHaveLength(2)

      act(() =>
        FakeWebSocket.last.simulateMessage({
          type: 'MESSAGE',
          conversationId: 'conversation-1',
          senderType: 'user',
          text: 'ping',
        }),
      )

      expect(screen.getAllByText('ping')).toHaveLength(1)
      expect(useChatStore.getState().messages['conversation-1']).toHaveLength(3)
    })
  })
})
