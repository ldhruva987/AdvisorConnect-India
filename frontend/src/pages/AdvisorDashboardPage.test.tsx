import { describe, expect, it } from 'vitest'
import { delay, http, HttpResponse } from 'msw'
import userEvent from '@testing-library/user-event'
import { server } from '@/test/mocks/server'
import { MOCK_CONVERSATION_DTOS } from '@/test/mocks/handlers/chat'
import { render, screen, waitFor, within } from '@/test/test-utils'
import type { Booking } from '@/types'
import type { ConversationDto } from '@/types/api'
import { AdvisorDashboardPage } from './AdvisorDashboardPage'

/**
 * Scope: the plan marks this page lower priority than the admin dashboard, so
 * these tests cover the load-bearing claim — every list is rendered from
 * `useConversations()` / `useMyBookings()` rather than the module-scope mock
 * arrays the page used to ship — plus the honest "coming soon" tabs. Row-level
 * formatting is already covered by `formatDateTime.test.ts`.
 */

const [FIRST_CONVERSATION, SECOND_CONVERSATION] = MOCK_CONVERSATION_DTOS

const DAY_MS = 24 * 60 * 60 * 1000

/**
 * Booking fixtures are built relative to `Date.now()` rather than reusing the
 * fixed-date `MOCK_BOOKINGS`. `isUpcoming` compares against the wall clock, so
 * a hardcoded 2026 instant would silently reclassify itself — and turn a green
 * suite red — the moment that date passes.
 */
function bookingAt(offsetDays: number, overrides: Partial<Booking> = {}): Booking {
  const sessionDate = new Date(Date.now() + offsetDays * DAY_MS).toISOString()
  return {
    id: `booking-${overrides.advisorUsername ?? offsetDays}`,
    advisorId: 'advisor-1',
    advisorUsername: 'maya_chen',
    sessionDate,
    durationMinutes: 30,
    amountCharged: 500,
    status: 'CONFIRMED',
    createdAt: new Date(Date.now() - DAY_MS).toISOString(),
    ...overrides,
  }
}

const SOONER_SESSION = bookingAt(2, { advisorUsername: 'maya_chen', amountCharged: 500 })
const LATER_SESSION = bookingAt(5, {
  advisorUsername: 'sam_okafor',
  amountCharged: 900,
  durationMinutes: 60,
  status: 'PENDING',
})
const PAST_SESSION = bookingAt(-3, { advisorUsername: 'jules_ferrand', status: 'COMPLETED' })
const CANCELLED_SESSION = bookingAt(1, { advisorUsername: 'riley_stone', status: 'CANCELLED' })
/** Completed but dated in the future — history, not an upcoming session. */
const COMPLETED_FUTURE_SESSION = bookingAt(6, { advisorUsername: 'dana_wu', status: 'COMPLETED' })

/** Install a `/bookings/me` response and record every request made to it. */
function serveBookings(bookings: Booking[]): number[] {
  const calls: number[] = []
  server.use(
    http.get('*/api/bookings/me', () => {
      calls.push(Date.now())
      return HttpResponse.json(bookings)
    }),
  )
  return calls
}

function serveConversations(conversations: ConversationDto[]): void {
  server.use(http.get('*/api/chats', () => HttpResponse.json(conversations)))
}

function failConversations(message = 'Chat service unavailable'): void {
  server.use(
    http.get('*/api/chats', () => HttpResponse.json({ message }, { status: 503 })),
  )
}

function failBookings(message = 'Bookings unavailable'): void {
  server.use(
    http.get('*/api/bookings/me', () => HttpResponse.json({ message }, { status: 500 })),
  )
}

/** A request that never settles, so an in-flight render is observable. */
function stallConversations(): void {
  server.use(http.get('*/api/chats', async () => { await delay('infinite') }))
}

function stallBookings(): void {
  server.use(http.get('*/api/bookings/me', async () => { await delay('infinite') }))
}

/**
 * The overview tile whose label is `label`, scoped through the tile grid.
 * "Upcoming Sessions" is both a tile label and a card heading, so an unscoped
 * `getByText` would be ambiguous. "Unread Messages" appears only on a tile,
 * which makes it a reliable handle on the grid.
 */
function statTile(label: string): HTMLElement {
  const grid = screen.getByText('Unread Messages').parentElement!.parentElement as HTMLElement
  return within(grid).getByText(label).parentElement as HTMLElement
}

async function openTab(user: ReturnType<typeof userEvent.setup>, name: RegExp | string) {
  await user.click(screen.getByRole('button', { name }))
}

describe('AdvisorDashboardPage', () => {
  /* ---------------------------------------------------------------------- */
  /* Overview                                                               */
  /* ---------------------------------------------------------------------- */

  describe('overview', () => {
    it('derives every stat tile from the API responses', async () => {
      serveConversations([
        ...MOCK_CONVERSATION_DTOS,
        { ...FIRST_CONVERSATION, id: 'conversation-3', advisorId: 'advisor-3', unreadCount: 3 },
      ])
      serveBookings([SOONER_SESSION, LATER_SESSION, PAST_SESSION])
      render(<AdvisorDashboardPage />)

      // 3 conversations, 2 + 0 + 3 unread, 2 of 3 bookings upcoming — three
      // distinct numbers, so a tile reading the wrong source is visible.
      await waitFor(() =>
        expect(within(statTile('Conversations')).getByText('3')).toBeInTheDocument(),
      )
      expect(within(statTile('Unread Messages')).getByText('5')).toBeInTheDocument()
      expect(within(statTile('Upcoming Sessions')).getByText('2')).toBeInTheDocument()
    })

    it('drops the invented rating and earnings tiles the mock page shipped', async () => {
      render(<AdvisorDashboardPage />)

      // Keyed off `lastMessage`, not the username: the default booking fixture
      // shares an advisor name with the default conversation fixture, so the
      // username matches two nodes on the overview.
      expect(await screen.findByText(FIRST_CONVERSATION.lastMessage)).toBeInTheDocument()
      expect(screen.queryByText('Avg Rating')).not.toBeInTheDocument()
      expect(screen.queryByText('4.9')).not.toBeInTheDocument()
      expect(screen.queryByText('$1,248')).not.toBeInTheDocument()
    })

    it('shows placeholders rather than zeroes before the queries settle', () => {
      stallConversations()
      stallBookings()
      render(<AdvisorDashboardPage />)

      expect(screen.getByText('Unread Messages')).toBeInTheDocument()
      expect(screen.queryByText('0')).not.toBeInTheDocument()
    })
  })

  /* ---------------------------------------------------------------------- */
  /* Chats tab                                                              */
  /* ---------------------------------------------------------------------- */

  describe('chats tab', () => {
    it('renders a loading state while the conversation request is in flight', async () => {
      const user = userEvent.setup()
      stallConversations()
      render(<AdvisorDashboardPage />)
      await openTab(user, /^chats/i)

      expect(screen.getAllByRole('status', { name: /loading conversations/i }).length)
        .toBeGreaterThan(0)
      expect(screen.queryByText(/no conversations/i)).not.toBeInTheDocument()
    })

    it('lists the conversations returned by GET /chats', async () => {
      const user = userEvent.setup()
      render(<AdvisorDashboardPage />)
      await openTab(user, /^chats/i)

      expect(await screen.findByText(FIRST_CONVERSATION.advisorUsername)).toBeInTheDocument()
      expect(screen.getByText(SECOND_CONVERSATION.advisorUsername)).toBeInTheDocument()
      expect(screen.getByText(FIRST_CONVERSATION.lastMessage)).toBeInTheDocument()
      expect(screen.getByText(SECOND_CONVERSATION.lastMessage)).toBeInTheDocument()
    })

    it('badges unread counts from the payload, not the hardcoded "3"', async () => {
      const user = userEvent.setup()
      render(<AdvisorDashboardPage />)

      // Sidebar badge is the summed real total (2 + 0).
      expect(await screen.findByRole('button', { name: 'Chats 2' })).toBeInTheDocument()

      await openTab(user, /^chats/i)
      // …and the row badge belongs to the conversation that actually has unread
      // messages; the read one gets no badge at all.
      expect(
        await screen.findByLabelText(`${FIRST_CONVERSATION.unreadCount} unread messages`),
      ).toBeInTheDocument()
      expect(screen.queryByLabelText('0 unread messages')).not.toBeInTheDocument()
    })

    it('filters the rendered list by the search box', async () => {
      const user = userEvent.setup()
      render(<AdvisorDashboardPage />)
      await openTab(user, /^chats/i)
      await screen.findByText(FIRST_CONVERSATION.advisorUsername)

      await user.type(screen.getByLabelText(/search conversations/i), 'sam')

      await waitFor(() =>
        expect(screen.queryByText(FIRST_CONVERSATION.advisorUsername)).not.toBeInTheDocument(),
      )
      expect(screen.getByText(SECOND_CONVERSATION.advisorUsername)).toBeInTheDocument()
    })

    it('explains an empty search result rather than showing a blank card', async () => {
      const user = userEvent.setup()
      render(<AdvisorDashboardPage />)
      await openTab(user, /^chats/i)
      await screen.findByText(FIRST_CONVERSATION.advisorUsername)

      await user.type(screen.getByLabelText(/search conversations/i), 'nobody-by-this-name')

      expect(await screen.findByText(/no conversation matches that search/i)).toBeInTheDocument()
    })

    it('shows an empty state when the API returns no conversations', async () => {
      const user = userEvent.setup()
      serveConversations([])
      render(<AdvisorDashboardPage />)
      await openTab(user, /^chats/i)

      expect(await screen.findByText('No conversations')).toBeInTheDocument()
      expect(
        screen.getByText(/messages from people you advise will show up here/i),
      ).toBeInTheDocument()
    })

    it('surfaces a retryable error instead of an empty inbox when the request fails', async () => {
      const user = userEvent.setup()
      failConversations()
      render(<AdvisorDashboardPage />)
      await openTab(user, /^chats/i)

      const banner = await screen.findByRole('alert')
      expect(within(banner).getByText('Chat service unavailable')).toBeInTheDocument()
      expect(within(banner).getByRole('button', { name: /try again/i })).toBeInTheDocument()
      // A failed fetch must not read as "you have no messages".
      expect(screen.queryByText('No conversations')).not.toBeInTheDocument()
    })
  })

  /* ---------------------------------------------------------------------- */
  /* Sessions tab                                                           */
  /* ---------------------------------------------------------------------- */

  describe('sessions tab', () => {
    it('renders a loading state while the bookings request is in flight', async () => {
      const user = userEvent.setup()
      stallBookings()
      render(<AdvisorDashboardPage />)
      await openTab(user, 'Sessions')

      expect(screen.getAllByRole('status', { name: /loading sessions/i }).length)
        .toBeGreaterThan(0)
      expect(screen.queryByText(/no upcoming sessions/i)).not.toBeInTheDocument()
    })

    it('renders bookings from GET /bookings/me, soonest first, with amounts in dollars', async () => {
      const user = userEvent.setup()
      // Supplied out of order: sorting must come from the page, not the payload.
      serveBookings([LATER_SESSION, SOONER_SESSION])
      render(<AdvisorDashboardPage />)
      await openTab(user, 'Sessions')

      const soonest = await screen.findByText(SOONER_SESSION.advisorUsername)
      const later = screen.getByText(LATER_SESSION.advisorUsername)
      expect(soonest.compareDocumentPosition(later)).toBe(Node.DOCUMENT_POSITION_FOLLOWING)

      // amountCharged is already a major-unit rupee decimal, not paise — ₹500.00, not a raw "500".
      expect(screen.getByText('₹500.00')).toBeInTheDocument()
      expect(screen.getByText('₹900.00')).toBeInTheDocument()
      expect(screen.queryByText('500')).not.toBeInTheDocument()
      expect(screen.getByText(/60 min/)).toBeInTheDocument()
    })

    it('lists only genuinely upcoming sessions', async () => {
      const user = userEvent.setup()
      serveBookings([SOONER_SESSION, PAST_SESSION, CANCELLED_SESSION, COMPLETED_FUTURE_SESSION])
      render(<AdvisorDashboardPage />)
      await openTab(user, 'Sessions')

      expect(await screen.findByText(SOONER_SESSION.advisorUsername)).toBeInTheDocument()
      expect(screen.queryByText(PAST_SESSION.advisorUsername)).not.toBeInTheDocument()
      expect(screen.queryByText(CANCELLED_SESSION.advisorUsername)).not.toBeInTheDocument()
      // Future-dated but already completed — still history.
      expect(screen.queryByText(COMPLETED_FUTURE_SESSION.advisorUsername)).not.toBeInTheDocument()
    })

    it('shows an empty state when nothing is booked', async () => {
      const user = userEvent.setup()
      serveBookings([PAST_SESSION])
      render(<AdvisorDashboardPage />)
      await openTab(user, 'Sessions')

      expect(await screen.findByText('No upcoming sessions')).toBeInTheDocument()
    })

    it('surfaces a retryable error and refetches on retry', async () => {
      const user = userEvent.setup()
      failBookings()
      render(<AdvisorDashboardPage />)
      await openTab(user, 'Sessions')

      const banner = await screen.findByRole('alert')
      expect(within(banner).getByText('Bookings unavailable')).toBeInTheDocument()

      const calls = serveBookings([SOONER_SESSION])
      await user.click(within(banner).getByRole('button', { name: /try again/i }))

      await waitFor(() => expect(calls.length).toBeGreaterThan(0))
      expect(await screen.findByText(SOONER_SESSION.advisorUsername)).toBeInTheDocument()
    })
  })

  /* ---------------------------------------------------------------------- */
  /* Tabs with no backend yet                                               */
  /* ---------------------------------------------------------------------- */

  describe('features with no backend yet', () => {
    it('says earnings are coming soon rather than inventing a payout figure', async () => {
      const user = userEvent.setup()
      render(<AdvisorDashboardPage />)
      await openTab(user, /earnings/i)

      expect(await screen.findByText(/earnings coming soon/i)).toBeInTheDocument()
      expect(
        screen.getByText(/payouts and transaction history will appear here/i),
      ).toBeInTheDocument()
      // The hardcoded ledger the old tab shipped.
      expect(screen.queryByText('$8,920')).not.toBeInTheDocument()
      expect(screen.queryByText('$428')).not.toBeInTheDocument()
    })

    it('says profile settings are coming soon rather than showing a dead form', async () => {
      const user = userEvent.setup()
      render(<AdvisorDashboardPage />)
      await openTab(user, /profile settings/i)

      expect(await screen.findByText(/profile settings coming soon/i)).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: /save changes/i })).not.toBeInTheDocument()
    })
  })
})
