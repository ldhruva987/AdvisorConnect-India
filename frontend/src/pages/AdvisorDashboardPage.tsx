import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  Home, MessageCircle, Video, DollarSign, Settings,
  ArrowLeft, Search, Inbox, CalendarClock,
} from 'lucide-react'
import { Avatar } from '@/shared/components/ui/Avatar'
import { Badge } from '@/shared/components/ui/Badge'
import { Card, CardHeader } from '@/shared/components/ui/Card'
import { EmptyState } from '@/shared/components/ui/EmptyState'
import { ErrorBanner } from '@/shared/components/ui/ErrorBanner'
import { Input } from '@/shared/components/ui/Input'
import { Skeleton } from '@/shared/components/ui/Skeleton'
import { useConversations } from '@/features/chat/hooks/useConversations'
import { useMyBookings } from '@/features/booking/hooks/useMyBookings'
import { bySessionDateAscending, formatBookingAmount, isUpcoming } from '@/lib/bookingDisplay'
import { formatConversationTime, formatSessionDate, formatSessionTime } from '@/lib/formatDateTime'
import { getErrorMessage } from '@/lib/getErrorMessage'
import { useNow } from '@/shared/hooks/useNow'
import type { Booking, Conversation } from '@/types'

type Tab = 'overview' | 'chats' | 'sessions' | 'earnings' | 'settings'

/** How many rows the overview previews before the dedicated tab takes over. */
const PREVIEW_ROWS = 4

function ConversationRowSkeleton() {
  return (
    <div className="flex items-center gap-3 p-4" role="status" aria-label="Loading conversations">
      <Skeleton className="w-10 h-10 rounded-full flex-shrink-0" />
      <div className="flex-1 space-y-2">
        <Skeleton className="h-3 w-2/5" />
        <Skeleton className="h-3 w-4/5" />
      </div>
    </div>
  )
}

function SessionRowSkeleton() {
  return (
    <div className="flex items-center gap-3 p-4" role="status" aria-label="Loading sessions">
      <Skeleton className="w-10 h-10 rounded-xl flex-shrink-0" />
      <div className="flex-1 space-y-2">
        <Skeleton className="h-3 w-1/3" />
        <Skeleton className="h-3 w-1/2" />
      </div>
      <Skeleton className="h-4 w-14" />
    </div>
  )
}

/**
 * One conversation row.
 *
 * NOTE ON NAMING: `ConversationDto` is user-centric — it carries
 * `advisorUsername`/`advisorColor` and no field for the *other* participant.
 * On an advisor's own dashboard the counterpart is the client, but the
 * contract does not expose them, so the row shows the only participant the
 * payload names. When chat-service ships its REST layer (backend Phase 8) with
 * a symmetric participant field, this reads that instead. Inventing a client
 * name here would be worse than showing the field that actually exists.
 */
function ConversationRow({ conversation }: { conversation: Conversation }) {
  return (
    <Link
      to={`/chat/${conversation.advisorId}`}
      className="flex items-center gap-3 p-4 hover:bg-ink-50 transition-colors"
    >
      <Avatar
        username={conversation.advisorUsername}
        color={conversation.advisorColor}
        size="md"
        showOnline={conversation.isAdvisorOnline}
      />
      <div className="flex-1 min-w-0">
        <div className="flex items-center justify-between gap-2 mb-0.5">
          <p className="text-sm font-semibold text-ink-900 truncate">
            {conversation.advisorUsername}
          </p>
          <p className="text-xs text-ink-400 flex-shrink-0">
            {formatConversationTime(conversation.lastMessageAt)}
          </p>
        </div>
        <p className="text-xs text-ink-500 truncate">{conversation.lastMessage}</p>
      </div>
      {conversation.unreadCount > 0 && (
        <span
          aria-label={`${conversation.unreadCount} unread messages`}
          className="w-5 h-5 flex-shrink-0 bg-oxblood-700 text-white text-xs font-bold rounded-full flex items-center justify-center"
        >
          {conversation.unreadCount}
        </span>
      )}
    </Link>
  )
}

function SessionRow({ booking }: { booking: Booking }) {
  return (
    <div className="flex items-center gap-3 p-4">
      <div className="w-10 h-10 bg-oxblood-50 rounded-xl flex items-center justify-center flex-shrink-0">
        <Video className="w-5 h-5 text-oxblood-700" aria-hidden="true" />
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-sm font-semibold text-ink-900 truncate">{booking.advisorUsername}</p>
        <p className="text-xs text-ink-400">
          {formatSessionDate(booking.sessionDate)} · {formatSessionTime(booking.sessionDate)} ·{' '}
          {booking.durationMinutes} min
        </p>
      </div>
      <span className="font-bold text-pine-600 flex-shrink-0">
        {formatBookingAmount(booking.amountCharged)}
      </span>
    </div>
  )
}

export function AdvisorDashboardPage() {
  const [activeTab, setActiveTab] = useState<Tab>('overview')
  const [chatSearch, setChatSearch] = useState('')

  const now = useNow()

  const conversationsQuery = useConversations()
  const bookingsQuery = useMyBookings()

  const conversations = useMemo(() => conversationsQuery.data ?? [], [conversationsQuery.data])
  const bookings = useMemo(() => bookingsQuery.data ?? [], [bookingsQuery.data])

  /**
   * `now` comes from `useNow` rather than module scope so a long-lived tab
   * doesn't keep classifying a session that has already started as "upcoming"
   * against a stale clock.
   */
  const upcomingSessions = useMemo(
    () =>
      bookings.filter((booking) => isUpcoming(booking, now)).sort(bySessionDateAscending),
    [bookings, now],
  )

  const unreadTotal = useMemo(
    () => conversations.reduce((sum, conversation) => sum + conversation.unreadCount, 0),
    [conversations],
  )

  const filteredConversations = useMemo(() => {
    const query = chatSearch.trim().toLowerCase()
    if (!query) return conversations
    return conversations.filter((c) => c.advisorUsername.toLowerCase().includes(query))
  }, [conversations, chatSearch])

  /**
   * Every tile is a count derived from data the API actually returned. The old
   * "Avg Rating 4.9" and "This Month $1,248" tiles are gone: no endpoint
   * serves an advisor's rating aggregate or earnings, and a fabricated figure
   * on a money dashboard is the least acceptable kind of placeholder.
   */
  const statTiles = [
    {
      label: 'Conversations',
      value: conversationsQuery.isSuccess ? conversations.length : null,
      color: 'text-oxblood-700',
      bg: 'bg-oxblood-50',
    },
    {
      label: 'Unread Messages',
      value: conversationsQuery.isSuccess ? unreadTotal : null,
      color: 'text-warn-600',
      bg: 'bg-warn-100',
    },
    {
      label: 'Upcoming Sessions',
      value: bookingsQuery.isSuccess ? upcomingSessions.length : null,
      color: 'text-pine-600',
      bg: 'bg-pine-100',
    },
  ]

  const navItems: { id: Tab; icon: React.ReactNode; label: string; badge?: number }[] = [
    { id: 'overview', icon: <Home className="w-4 h-4" />, label: 'Overview' },
    {
      id: 'chats',
      icon: <MessageCircle className="w-4 h-4" />,
      label: 'Chats',
      // Real unread total, not the hardcoded "3" the old sidebar always showed.
      badge: unreadTotal > 0 ? unreadTotal : undefined,
    },
    { id: 'sessions', icon: <Video className="w-4 h-4" />, label: 'Sessions' },
    { id: 'earnings', icon: <DollarSign className="w-4 h-4" />, label: 'Earnings' },
    { id: 'settings', icon: <Settings className="w-4 h-4" />, label: 'Profile Settings' },
  ]

  const today = new Date().toLocaleDateString('en-US', {
    weekday: 'long',
    month: 'long',
    day: 'numeric',
    year: 'numeric',
  })

  /** Conversation list body, shared by the overview preview and the Chats tab. */
  const renderConversations = (list: Conversation[], emptyDescription: string) => {
    if (conversationsQuery.isLoading) {
      return (
        <div className="divide-y divide-ink-100">
          {Array.from({ length: 3 }, (_, i) => <ConversationRowSkeleton key={i} />)}
        </div>
      )
    }

    if (conversationsQuery.isError) {
      return (
        <div className="p-4">
          <ErrorBanner
            message={getErrorMessage(conversationsQuery.error)}
            onRetry={() => void conversationsQuery.refetch()}
          />
        </div>
      )
    }

    if (list.length === 0) {
      return (
        <EmptyState
          icon={<Inbox className="w-8 h-8" />}
          title="No conversations"
          description={emptyDescription}
          className="py-12"
        />
      )
    }

    return (
      <div className="divide-y divide-ink-100">
        {list.map((conversation) => (
          <ConversationRow key={conversation.id} conversation={conversation} />
        ))}
      </div>
    )
  }

  /** Upcoming-session list body, shared by the overview preview and the Sessions tab. */
  const renderSessions = (list: Booking[]) => {
    if (bookingsQuery.isLoading) {
      return (
        <div className="divide-y divide-ink-100">
          {Array.from({ length: 2 }, (_, i) => <SessionRowSkeleton key={i} />)}
        </div>
      )
    }

    if (bookingsQuery.isError) {
      return (
        <div className="p-4">
          <ErrorBanner
            message={getErrorMessage(bookingsQuery.error)}
            onRetry={() => void bookingsQuery.refetch()}
          />
        </div>
      )
    }

    if (list.length === 0) {
      return (
        <EmptyState
          icon={<CalendarClock className="w-8 h-8" />}
          title="No upcoming sessions"
          description="Booked video sessions will appear here."
          className="py-12"
        />
      )
    }

    return (
      <div className="divide-y divide-ink-100">
        {list.map((booking) => (
          <SessionRow key={booking.id} booking={booking} />
        ))}
      </div>
    )
  }

  return (
    <div className="flex h-screen bg-ink-50 overflow-hidden">
      {/* Sidebar */}
      <aside className="w-60 bg-white border-r border-ink-200 flex flex-col flex-shrink-0">
        {/*
          The old header rendered "@MindfulRohan", a "Licensed Clinical
          Psychologist" title and a 4.9 rating for whoever happened to be
          logged in. Nothing serves the signed-in advisor's own profile yet
          (`authStore` holds an id and a role, no username), so the panel
          names the surface instead of inventing a person.
        */}
        <div className="p-5 border-b border-ink-100">
          <p className="text-xs font-semibold text-ink-400 uppercase tracking-wider mb-1">
            Advisor
          </p>
          <p className="font-heading font-medium text-lg text-ink-900">Your dashboard</p>
        </div>

        {/* Nav */}
        <nav className="flex-1 p-3 space-y-1">
          {navItems.map((item) => (
            <button
              key={item.id}
              onClick={() => setActiveTab(item.id)}
              aria-current={activeTab === item.id ? 'page' : undefined}
              className={`w-full flex items-center justify-between px-3 py-2.5 rounded-xl text-sm font-medium transition-all ${
                activeTab === item.id
                  ? 'bg-oxblood-50 text-oxblood-700'
                  : 'text-ink-600 hover:bg-ink-50 hover:text-ink-900'
              }`}
            >
              <div className="flex items-center gap-2.5">
                {item.icon}
                {item.label}
              </div>
              {item.badge !== undefined && (
                <span className="min-w-5 h-5 px-1 bg-oxblood-700 text-white text-xs font-bold rounded-full flex items-center justify-center">
                  {item.badge}
                </span>
              )}
            </button>
          ))}
        </nav>

        {/* Exit */}
        <div className="p-4 border-t border-ink-100">
          <Link
            to="/"
            className="flex items-center gap-2 text-sm text-ink-500 hover:text-ink-900 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Exit dashboard
          </Link>
        </div>
      </aside>

      {/* Main area */}
      <main className="flex-1 overflow-y-auto p-6">
        {/* OVERVIEW TAB */}
        {activeTab === 'overview' && (
          <div>
            <div className="mb-6">
              <h1 className="font-heading font-medium text-2xl text-ink-900">Overview</h1>
              <p className="text-ink-500 text-sm mt-0.5">{today}</p>
            </div>

            {/* Stat tiles */}
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-6">
              {statTiles.map((tile) => (
                <div key={tile.label} className={`${tile.bg} rounded-xl p-5`}>
                  <p className="text-xs font-semibold text-ink-500 uppercase tracking-wider mb-1">
                    {tile.label}
                  </p>
                  {tile.value === null ? (
                    <Skeleton className="h-8 w-16" />
                  ) : (
                    <p className={`font-heading font-medium text-2xl ${tile.color}`}>
                      {tile.value.toLocaleString('en-US')}
                    </p>
                  )}
                </div>
              ))}
            </div>

            <div className="grid lg:grid-cols-2 gap-5">
              <Card padding="none">
                <CardHeader>
                  <h2 className="font-semibold text-ink-900">Recent Conversations</h2>
                  {conversationsQuery.isSuccess && conversations.length > 0 && (
                    <Badge variant="brand">{conversations.length}</Badge>
                  )}
                </CardHeader>
                {renderConversations(
                  conversations.slice(0, PREVIEW_ROWS),
                  'Messages from people you advise will show up here.',
                )}
              </Card>

              <Card padding="none">
                <CardHeader>
                  <h2 className="font-semibold text-ink-900">Upcoming Sessions</h2>
                  {bookingsQuery.isSuccess && upcomingSessions.length > 0 && (
                    <Badge variant="brand">{upcomingSessions.length}</Badge>
                  )}
                </CardHeader>
                {renderSessions(upcomingSessions.slice(0, PREVIEW_ROWS))}
              </Card>
            </div>
          </div>
        )}

        {/* CHATS TAB */}
        {activeTab === 'chats' && (
          <div>
            <h1 className="font-heading font-medium text-2xl text-ink-900 mb-5">My Chats</h1>
            <div className="max-w-md mb-4">
              <Input
                // Labelled for screen readers without a visible label, which
                // the surrounding layout has no room for.
                aria-label="Search conversations"
                placeholder="Search conversations..."
                value={chatSearch}
                onChange={(e) => setChatSearch(e.target.value)}
                leftIcon={<Search className="w-4 h-4" />}
              />
            </div>
            <Card padding="none">
              {renderConversations(
                filteredConversations,
                chatSearch.trim()
                  ? 'No conversation matches that search.'
                  : 'Messages from people you advise will show up here.',
              )}
            </Card>
          </div>
        )}

        {/* SESSIONS TAB */}
        {activeTab === 'sessions' && (
          <div>
            <h1 className="font-heading font-medium text-2xl text-ink-900 mb-5">Video Sessions</h1>
            <Card padding="none">
              <CardHeader>
                <h2 className="font-semibold text-ink-900">Upcoming Sessions</h2>
              </CardHeader>
              {renderSessions(upcomingSessions)}
            </Card>
            {/*
              Past sessions are deliberately not a second list here: `GET
              /bookings/me` returns everything, but a "history" tab implies
              per-session artefacts (recordings, notes, receipts) that no
              endpoint serves. The upcoming filter is the honest slice.
            */}
          </div>
        )}

        {/* EARNINGS TAB */}
        {activeTab === 'earnings' && (
          <div>
            <h1 className="font-heading font-medium text-2xl text-ink-900 mb-5">Earnings</h1>
            <Card>
              {/*
                The old tab showed "$8,920 total", "$428 pending payout" and a
                transaction ledger, all hardcoded. There is no payouts or
                earnings API — booking amounts are what a *client* was charged,
                not what an advisor is owed after platform fees — so nothing
                here can be derived honestly from what exists today.
              */}
              <EmptyState
                icon={<DollarSign className="w-8 h-8" />}
                title="Earnings coming soon"
                description="Payouts and transaction history will appear here once payments reporting is available."
              />
            </Card>
          </div>
        )}

        {/* SETTINGS TAB */}
        {activeTab === 'settings' && (
          <div>
            <h1 className="font-heading font-medium text-2xl text-ink-900 mb-5">Profile Settings</h1>
            <Card>
              <EmptyState
                icon={<Settings className="w-8 h-8" />}
                title="Profile settings coming soon"
                description="Editing your public advisor profile is not available yet."
              />
            </Card>
          </div>
        )}
      </main>
    </div>
  )
}
