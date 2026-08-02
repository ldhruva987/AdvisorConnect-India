import { useEffect, useId, useRef, useState } from 'react'
import { Bell } from 'lucide-react'
import { Skeleton } from '@/shared/components/ui/Skeleton'
import { cn } from '@/lib/utils'
import { formatConversationTime } from '@/lib/formatDateTime'
import { getErrorMessage } from '@/lib/getErrorMessage'
import { useNotifications } from '../hooks/useNotifications'
import { useMarkNotificationRead } from '../hooks/useMarkNotificationRead'
import type { NotificationDto } from '@/types/api'

/** Unread counts above this render as "9+" rather than widening the badge. */
const MAX_BADGE_COUNT = 9

interface NotificationRowProps {
  notification: NotificationDto
  onMarkRead: (id: string) => void
}

/**
 * One inbox row. The whole row is the mark-read affordance rather than a
 * separate "mark read" button: opening a notification *is* reading it, and a
 * dedicated button in a 360px panel would compete with the text for space.
 * Already-read rows render as a plain non-interactive block so there is no
 * button that visibly does nothing.
 */
function NotificationRow({ notification, onMarkRead }: NotificationRowProps) {
  const timestamp = formatConversationTime(notification.createdAt)

  const content = (
    <>
      <span
        className={cn(
          'w-2 h-2 rounded-full flex-shrink-0 mt-1.5',
          notification.read ? 'bg-transparent' : 'bg-oxblood-700',
        )}
        aria-hidden="true"
      />
      <span className="flex-1 min-w-0">
        <span className="flex items-baseline justify-between gap-2">
          <span
            className={cn(
              'text-sm truncate',
              notification.read ? 'text-ink-700 font-medium' : 'text-ink-900 font-semibold',
            )}
          >
            {notification.title}
          </span>
          <span className="text-xs text-ink-400 flex-shrink-0">{timestamp}</span>
        </span>
        <span className="block text-xs text-ink-500 mt-0.5 line-clamp-2">{notification.body}</span>
      </span>
    </>
  )

  if (notification.read) {
    return (
      <li className="flex gap-2.5 px-4 py-3 border-b border-ink-100 last:border-b-0">{content}</li>
    )
  }

  return (
    <li className="border-b border-ink-100 last:border-b-0">
      <button
        type="button"
        onClick={() => onMarkRead(notification.id)}
        aria-label={`Mark "${notification.title}" as read`}
        className="w-full flex gap-2.5 px-4 py-3 text-left hover:bg-ink-50 transition-colors focus-visible:outline-none focus-visible:bg-ink-50"
      >
        {content}
      </button>
    </li>
  )
}

/**
 * The Navbar's notifications bell and its anchored panel.
 *
 * Deliberately *not* built on the shared `Modal`: that component is a
 * fixed-inset centred overlay with a dimming backdrop, which is the wrong
 * physics for a bell-anchored dropdown — it would darken the page and detach
 * the panel from the control that opened it. This is the cheaper, correct
 * shape: an absolutely-positioned panel inside a `relative` wrapper.
 *
 * Dismissal mirrors `Modal`'s Escape-key `useEffect` (same document-level
 * listener, same cleanup) and adds a pointerdown listener for outside clicks.
 * Both are registered only while open, so a closed bell costs nothing.
 */
export function NotificationDropdown() {
  const [open, setOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)
  const panelId = useId()

  const { data, isLoading, isError, error } = useNotifications()
  const markRead = useMarkNotificationRead()

  const notifications = data ?? []
  const unreadCount = notifications.filter((n) => !n.read).length

  // Escape to close — same pattern as `Modal`, kept consistent on purpose.
  useEffect(() => {
    if (!open) return
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false)
    }
    document.addEventListener('keydown', handler)
    return () => document.removeEventListener('keydown', handler)
  }, [open])

  // Outside click to close. `pointerdown` rather than `click` so the panel is
  // gone before the click lands on whatever is underneath, which is what makes
  // "click the bell again to close" work instead of closing and reopening.
  useEffect(() => {
    if (!open) return
    const handler = (e: PointerEvent) => {
      if (!containerRef.current?.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('pointerdown', handler)
    return () => document.removeEventListener('pointerdown', handler)
  }, [open])

  return (
    <div ref={containerRef} className="relative">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-label={
          unreadCount > 0 ? `Notifications (${unreadCount} unread)` : 'Notifications'
        }
        aria-expanded={open}
        aria-haspopup="menu"
        aria-controls={open ? panelId : undefined}
        className="relative w-9 h-9 rounded-lg text-ink-600 hover:bg-ink-100 hover:text-ink-900 flex items-center justify-center transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-oxblood-600 focus-visible:ring-offset-2"
      >
        <Bell className="w-5 h-5" aria-hidden="true" />
        {unreadCount > 0 && (
          // Same treatment as the chat list's unread pill (oxblood circle,
          // white bold text) so the two unread signals read as one system.
          <span
            aria-hidden="true"
            className="absolute -top-0.5 -right-0.5 min-w-[1.125rem] h-[1.125rem] px-1 rounded-full bg-oxblood-700 text-white text-[0.625rem] font-bold flex items-center justify-center ring-2 ring-white"
          >
            {unreadCount > MAX_BADGE_COUNT ? `${MAX_BADGE_COUNT}+` : unreadCount}
          </span>
        )}
      </button>

      {open && (
        <div
          id={panelId}
          role="menu"
          aria-label="Notifications"
          className="absolute right-0 top-full mt-2 w-80 max-w-[calc(100vw-2rem)] bg-white rounded-xl border border-ink-200 shadow-overlay overflow-hidden z-50"
        >
          <div className="px-4 py-3 border-b border-ink-100 flex items-center justify-between">
            <span className="text-sm font-semibold text-ink-900">Notifications</span>
            {unreadCount > 0 && (
              <span className="text-xs text-ink-400">{unreadCount} unread</span>
            )}
          </div>

          <div className="max-h-80 overflow-y-auto">
            {isLoading ? (
              // Compact skeletons rather than a spinner: the panel is a fixed
              // width, so placeholder rows hold the shape data will arrive in.
              <div className="p-4 space-y-4" role="status" aria-label="Loading notifications">
                {Array.from({ length: 2 }, (_, i) => (
                  <div key={i} className="space-y-2">
                    <Skeleton className="h-3 w-2/5" />
                    <Skeleton className="h-3 w-4/5" />
                  </div>
                ))}
              </div>
            ) : isError ? (
              // A compact inline message, not the full `ErrorBanner` — a
              // retry-and-alert treatment is too much ceremony for a panel the
              // 30-second poll will refresh on its own anyway.
              <p role="alert" className="px-4 py-6 text-sm text-danger-600 text-center">
                {getErrorMessage(error)}
              </p>
            ) : notifications.length === 0 ? (
              <p className="px-4 py-8 text-sm text-ink-400 text-center">No notifications</p>
            ) : (
              <ul>
                {notifications.map((notification) => (
                  <NotificationRow
                    key={notification.id}
                    notification={notification}
                    onMarkRead={(id) => markRead.mutate(id)}
                  />
                ))}
              </ul>
            )}
          </div>
        </div>
      )}
    </div>
  )
}
