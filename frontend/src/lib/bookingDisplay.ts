import { formatCurrency } from '@/lib/utils'
import type { Booking } from '@/types'

/**
 * Presentation rules for a `Booking`, shared by AdvisorDashboardPage's
 * Sessions tab and MyBookingsPage.
 *
 * Lives in `lib/` for the same reason `formatDateTime.ts` does: both pages
 * render the same bookings and must agree on what counts as "upcoming" and how
 * a charge reads. Two private copies would drift, and the two pages disagreeing
 * about whether a session is still ahead of you is a bug a user would notice.
 */

/**
 * A booking is "upcoming" if it hasn't started yet and hasn't been closed out.
 * `COMPLETED` is excluded as well as `CANCELLED`: a session marked complete is
 * history even if its clock time is somehow still in the future.
 *
 * An unparseable `sessionDate` sorts to the past rather than the future — a
 * booking we can't place in time shouldn't sit at the top of the page
 * pretending to be your next appointment.
 */
export function isUpcoming(booking: Booking, now: number): boolean {
  const startsAt = new Date(booking.sessionDate).getTime()
  if (Number.isNaN(startsAt)) return false
  return startsAt >= now && booking.status !== 'CANCELLED' && booking.status !== 'COMPLETED'
}

/** `Booking.amountCharged` is minor units (cents), per the booking contract. */
export function formatBookingAmount(minorUnits: number): string {
  return formatCurrency(minorUnits / 100)
}

/** Chronological, soonest first. */
export function bySessionDateAscending(a: Booking, b: Booking): number {
  return new Date(a.sessionDate).getTime() - new Date(b.sessionDate).getTime()
}

/** Reverse-chronological, most recent first — how history reads. */
export function bySessionDateDescending(a: Booking, b: Booking): number {
  return new Date(b.sessionDate).getTime() - new Date(a.sessionDate).getTime()
}

/**
 * `Booking.status` → `Badge` variant. Distinct from `StatusBadge` in
 * `Badge.tsx`, which maps the unrelated `ApplicationStatus` enum.
 */
export const BOOKING_STATUS_VARIANT = {
  PENDING: 'pending',
  CONFIRMED: 'success',
  COMPLETED: 'default',
  CANCELLED: 'danger',
} as const satisfies Record<Booking['status'], string>

/** Sentence-case labels; the raw enum is not user-facing copy. */
export const BOOKING_STATUS_LABEL = {
  PENDING: 'Pending',
  CONFIRMED: 'Confirmed',
  COMPLETED: 'Completed',
  CANCELLED: 'Cancelled',
} as const satisfies Record<Booking['status'], string>
