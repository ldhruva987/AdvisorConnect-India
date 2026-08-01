import { useState } from 'react'
import { Link } from 'react-router-dom'
import {
  Home, MessageCircle, Video, DollarSign, Settings,
  TrendingUp, ArrowLeft, Search, Star,
} from 'lucide-react'
import { Avatar } from '@/shared/components/ui/Avatar'
import { Badge } from '@/shared/components/ui/Badge'
import { Card, CardHeader } from '@/shared/components/ui/Card'
import { Input } from '@/shared/components/ui/Input'

type Tab = 'overview' | 'chats' | 'sessions' | 'earnings' | 'settings'

const STATS = [
  { label: 'Total Chats', value: '1,247', delta: '+12%', color: 'text-oxblood-700', bg: 'bg-oxblood-50' },
  { label: 'Video Sessions', value: '89', delta: '+8%', color: 'text-pine-600', bg: 'bg-pine-100' },
  { label: 'Avg Rating', value: '4.9', delta: 'Excellent', color: 'text-warn-600', bg: 'bg-warn-100' },
  { label: 'This Month', value: '$1,248', delta: '+18%', color: 'text-ink-700', bg: 'bg-ink-100' },
]

const ACTIVE_CHATS = [
  { id: '1', username: '@AnxiousAnna', color: '#784f00', lastMessage: 'Thank you so much, that really helps!', time: '2m ago', unread: 2 },
  { id: '2', username: '@OverwhelmedOliver', color: '#516000', lastMessage: 'Can we schedule a follow-up session?', time: '15m ago', unread: 1 },
  { id: '3', username: '@StressedSophia', color: '#006970', lastMessage: 'I tried the breathing exercise and it worked!', time: '1h ago', unread: 0 },
]

const UPCOMING_SESSIONS = [
  { id: '1', username: '@AnxiousAnna', color: '#784f00', date: 'Today', time: '3:00 PM', duration: 30, amount: 39 },
  { id: '2', username: '@FirstTimeBuyerJoe', color: '#005e8f', date: 'Mar 18', time: '11:00 AM', duration: 60, amount: 69 },
]

const CONVERSATIONS = [
  { id: '1', username: '@AnxiousAnna', color: '#784f00', lastMessage: 'Thank you so much, that really helps!', time: '2m ago', status: 'active' },
  { id: '2', username: '@OverwhelmedOliver', color: '#516000', lastMessage: 'Can we schedule a follow-up session?', time: '15m ago', status: 'active' },
  { id: '3', username: '@StressedSophia', color: '#006970', lastMessage: 'I tried the breathing exercise and it worked!', time: '1h ago', status: 'resolved' },
  { id: '4', username: '@WorriedWendy', color: '#4d4f94', lastMessage: 'Is it normal to feel this way after CBT?', time: '2h ago', status: 'active' },
  { id: '5', username: '@BurnedOutBen', color: '#73417e', lastMessage: 'I really needed to hear that, thank you.', time: 'Yesterday', status: 'resolved' },
]

const TRANSACTIONS = [
  { id: '1', label: 'Video Session — @AnxiousAnna', date: 'Mar 15, 2026', amount: '+$39' },
  { id: '2', label: 'Video Session — @OverwhelmedOliver', date: 'Mar 13, 2026', amount: '+$69' },
  { id: '3', label: 'Video Session — @StressedSophia', date: 'Mar 11, 2026', amount: '+$39' },
  { id: '4', label: 'Video Session — @WorriedWendy', date: 'Mar 9, 2026', amount: '+$69' },
]

const NAV_ITEMS: { id: Tab; icon: React.ReactNode; label: string; badge?: number }[] = [
  { id: 'overview', icon: <Home className="w-4 h-4" />, label: 'Overview' },
  { id: 'chats', icon: <MessageCircle className="w-4 h-4" />, label: 'Chats', badge: 3 },
  { id: 'sessions', icon: <Video className="w-4 h-4" />, label: 'Sessions' },
  { id: 'earnings', icon: <DollarSign className="w-4 h-4" />, label: 'Earnings' },
  { id: 'settings', icon: <Settings className="w-4 h-4" />, label: 'Profile Settings' },
]

export function AdvisorDashboardPage() {
  const [activeTab, setActiveTab] = useState<Tab>('overview')
  const [chatSearch, setChatSearch] = useState('')

  return (
    <div className="flex h-screen bg-ink-50 overflow-hidden">
      {/* Sidebar */}
      <aside className="w-60 bg-white border-r border-ink-200 flex flex-col flex-shrink-0">
        {/* Advisor summary */}
        <div className="p-5 border-b border-ink-100">
          <div className="flex items-center gap-3 mb-3">
            <Avatar username="@MindfulRohan" color="#8a3f24" size="md" showOnline />
            <div className="min-w-0">
              <p className="font-semibold text-ink-900 text-sm truncate">@MindfulRohan</p>
              <Badge variant="live" className="mt-0.5">● Active</Badge>
            </div>
          </div>
        </div>

        {/* Nav */}
        <nav className="flex-1 p-3 space-y-1">
          {NAV_ITEMS.map((item) => (
            <button
              key={item.id}
              onClick={() => setActiveTab(item.id)}
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
              {item.badge && (
                <span className="w-5 h-5 bg-oxblood-700 text-white text-xs font-bold rounded-full flex items-center justify-center">
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
            <div className="flex items-center justify-between mb-6">
              <div>
                <h1 className="font-heading font-medium text-2xl text-ink-900">Welcome back, Rohan!</h1>
                <p className="text-ink-500 text-sm mt-0.5">Here's what's happening today.</p>
              </div>
              <Badge variant="live">● Profile Live</Badge>
            </div>

            {/* Stat cards */}
            <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
              {STATS.map((stat) => (
                <div key={stat.label} className={`${stat.bg} rounded-xl p-5`}>
                  <p className="text-xs font-semibold text-ink-500 uppercase tracking-wider mb-1">{stat.label}</p>
                  <p className={`font-heading font-medium text-2xl ${stat.color}`}>{stat.value}</p>
                  <p className="text-xs text-ink-500 mt-1 flex items-center gap-1">
                    <TrendingUp className="w-3 h-3" />
                    {stat.delta}
                  </p>
                </div>
              ))}
            </div>

            {/* Two-column grid */}
            <div className="grid lg:grid-cols-2 gap-5">
              {/* Active chats */}
              <Card padding="none">
                <CardHeader>
                  <h2 className="font-semibold text-ink-900">Active Chats</h2>
                  <Badge variant="brand">{ACTIVE_CHATS.length}</Badge>
                </CardHeader>
                <div className="divide-y divide-ink-100">
                  {ACTIVE_CHATS.map((chat) => (
                    <div key={chat.id} className="flex items-center gap-3 p-4">
                      <Avatar username={chat.username} color={chat.color} size="sm" showOnline />
                      <div className="flex-1 min-w-0">
                        <p className="text-sm font-semibold text-ink-900">{chat.username}</p>
                        <p className="text-xs text-ink-400 truncate">{chat.lastMessage}</p>
                      </div>
                      <div className="text-right flex-shrink-0">
                        <p className="text-xs text-ink-400">{chat.time}</p>
                        {chat.unread > 0 && (
                          <span className="inline-flex w-5 h-5 bg-oxblood-700 text-white text-xs font-bold rounded-full items-center justify-center mt-1">
                            {chat.unread}
                          </span>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              </Card>

              {/* Upcoming sessions */}
              <Card padding="none">
                <CardHeader>
                  <h2 className="font-semibold text-ink-900">Upcoming Sessions</h2>
                  <Badge variant="brand">{UPCOMING_SESSIONS.length}</Badge>
                </CardHeader>
                <div className="divide-y divide-ink-100">
                  {UPCOMING_SESSIONS.map((session) => (
                    <div key={session.id} className="flex items-center gap-3 p-4">
                      <Avatar username={session.username} color={session.color} size="sm" />
                      <div className="flex-1 min-w-0">
                        <p className="text-sm font-semibold text-ink-900">{session.username}</p>
                        <p className="text-xs text-ink-400">{session.date} · {session.time}</p>
                      </div>
                      <div className="text-right flex-shrink-0">
                        <p className="text-sm font-bold text-pine-600">${session.amount}</p>
                        <p className="text-xs text-ink-400">{session.duration}min</p>
                      </div>
                    </div>
                  ))}
                </div>
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
                placeholder="Search conversations..."
                value={chatSearch}
                onChange={(e) => setChatSearch(e.target.value)}
                leftIcon={<Search className="w-4 h-4" />}
              />
            </div>
            <Card padding="none">
              <div className="divide-y divide-ink-100">
                {CONVERSATIONS.filter((c) =>
                  c.username.toLowerCase().includes(chatSearch.toLowerCase())
                ).map((chat) => (
                  <div key={chat.id} className="flex items-center gap-3 p-4 hover:bg-ink-50 transition-colors">
                    <Avatar username={chat.username} color={chat.color} size="md" showOnline={chat.status === 'active'} />
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center justify-between mb-0.5">
                        <p className="text-sm font-semibold text-ink-900">{chat.username}</p>
                        <p className="text-xs text-ink-400">{chat.time}</p>
                      </div>
                      <p className="text-xs text-ink-500 truncate">{chat.lastMessage}</p>
                    </div>
                    <Badge variant={chat.status === 'active' ? 'success' : 'default'}>
                      {chat.status === 'active' ? 'Active' : 'Resolved'}
                    </Badge>
                  </div>
                ))}
              </div>
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
              <div className="divide-y divide-ink-100">
                {UPCOMING_SESSIONS.map((session) => (
                  <div key={session.id} className="flex items-center gap-3 p-5">
                    <div className="w-10 h-10 bg-oxblood-50 rounded-xl flex items-center justify-center">
                      <Video className="w-5 h-5 text-oxblood-700" />
                    </div>
                    <div className="flex-1">
                      <p className="font-semibold text-ink-900 text-sm">{session.username}</p>
                      <p className="text-xs text-ink-400">{session.date} · {session.time} · {session.duration} min</p>
                    </div>
                    <span className="font-bold text-pine-600">${session.amount}</span>
                  </div>
                ))}
              </div>
            </Card>
          </div>
        )}

        {/* EARNINGS TAB */}
        {activeTab === 'earnings' && (
          <div>
            <h1 className="font-heading font-medium text-2xl text-ink-900 mb-5">Earnings</h1>

            {/* Metric cards */}
            <div className="grid grid-cols-3 gap-4 mb-6">
              {[
                { label: 'This Month', value: '$1,248', color: 'text-pine-600' },
                { label: 'Total Earnings', value: '$8,920', color: 'text-oxblood-700' },
                { label: 'Pending Payout', value: '$428', color: 'text-warn-600' },
              ].map((m) => (
                <div key={m.label} className="bg-white rounded-xl border border-ink-200 p-5">
                  <p className="text-xs font-semibold text-ink-500 uppercase tracking-wider mb-1">{m.label}</p>
                  <p className={`font-heading font-medium text-2xl ${m.color}`}>{m.value}</p>
                </div>
              ))}
            </div>

            {/* Transactions table */}
            <Card padding="none">
              <CardHeader>
                <h2 className="font-semibold text-ink-900">Recent Transactions</h2>
              </CardHeader>
              <div className="divide-y divide-ink-100">
                {TRANSACTIONS.map((tx) => (
                  <div key={tx.id} className="flex items-center justify-between p-4">
                    <div>
                      <p className="text-sm font-medium text-ink-800">{tx.label}</p>
                      <p className="text-xs text-ink-400">{tx.date}</p>
                    </div>
                    <span className="font-bold text-pine-600">{tx.amount}</span>
                  </div>
                ))}
              </div>
            </Card>
          </div>
        )}

        {/* SETTINGS TAB */}
        {activeTab === 'settings' && (
          <div>
            <h1 className="font-heading font-medium text-2xl text-ink-900 mb-5">Profile Settings</h1>
            <Card>
              <div className="flex items-center gap-4 mb-6">
                <Avatar username="@MindfulRohan" color="#8a3f24" size="xl" showOnline />
                <div>
                  <h2 className="font-heading font-semibold text-xl text-ink-900">@MindfulRohan</h2>
                  <p className="text-ink-500 text-sm">Licensed Clinical Psychologist</p>
                  <div className="flex items-center gap-1.5 mt-1">
                    <Star className="w-4 h-4 text-warn-500 fill-warn-500" />
                    <span className="text-sm font-semibold">4.9</span>
                    <span className="text-xs text-ink-400">(342 reviews)</span>
                  </div>
                </div>
              </div>
              <p className="text-sm text-ink-500 text-center py-6 bg-ink-50 rounded-xl">
                Profile settings editing coming soon in the full version.
              </p>
            </Card>
          </div>
        )}
      </main>
    </div>
  )
}
