/**
 * Date/time formatting shared by the conversation and session lists.
 *
 * Lives in `lib/` rather than inside one page because ChatPage and
 * AdvisorDashboardPage render the same conversation rows and must agree on
 * how a timestamp reads — two private copies would drift.
 *
 * Everything here is defensive about bad input: these values come off the
 * wire, and a malformed instant should degrade to a dash rather than render
 * "Invalid Date" at the user.
 */

/** Rendered wherever a value is missing or unparseable. */
export const EM_DASH = '—'

function parse(iso: string): Date | null {
  const date = new Date(iso)
  return Number.isNaN(date.getTime()) ? null : date
}

/**
 * Conversation-list timestamp: clock time if it happened today, otherwise the
 * calendar date. Matches the convention every messaging client uses.
 */
export function formatConversationTime(iso: string): string {
  const date = parse(iso)
  if (!date) return iso
  const isToday = date.toDateString() === new Date().toDateString()
  return isToday
    ? date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    : date.toLocaleDateString([], { month: 'short', day: 'numeric' })
}

/** "Aug 14, 2026" — for a booked session's date. */
export function formatSessionDate(iso: string): string {
  const date = parse(iso)
  if (!date) return EM_DASH
  return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
}

/** "9:00 AM" — for a booked session's start time. */
export function formatSessionTime(iso: string): string {
  const date = parse(iso)
  if (!date) return EM_DASH
  return date.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' })
}
