import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { CalendarClock, Video } from 'lucide-react'
import { Badge } from '@/shared/components/ui/Badge'
import { Button } from '@/shared/components/ui/Button'
import { EmptyState } from '@/shared/components/ui/EmptyState'
import { ErrorBanner } from '@/shared/components/ui/ErrorBanner'
import { Skeleton } from '@/shared/components/ui/Skeleton'
import { Navbar } from '@/shared/components/layout/Navbar'
import { useNow } from '@/shared/hooks/useNow'
import { useMyBookings } from '@/features/booking/hooks/useMyBookings'
import { ReviewModal } from '@/features/advisor-profile/components/ReviewModal'
import {
  BOOKING_STATUS_LABEL,
  BOOKING_STATUS_VARIANT,
  bySessionDateAscending,
  bySessionDateDescending,
  formatBookingAmount,
  isUpcoming,
} from '@/lib/bookingDisplay'
import { formatSessionDate, formatSessionTime } from '@/lib/formatDateTime'
import { getErrorMessage } from '@/lib/getErrorMessage'
import type { Booking } from '@/types'

/** Identifies which booking the review modal is open for. */
type ReviewTarget = Pick<Booking, 'id' | 'advisorId' | 'advisorUsername'>

function BookingRowSkeleton() {
  return (
    <div className="flex items-center gap-4 p-4 border-b border-ink-100 last:border-b-0">
      <Skeleton className="w-11 h-11 rounded-xl flex-shrink-0" />
      <div className="flex-1 space-y-2">
        <Skeleton className="h-3.5 w-1/4" />
        <Skeleton className="h-3 w-2/5" />
      </div>
      <Skeleton className="h-4 w-16" />
    </div>
  )
}

interface BookingRowProps {
  booking: Booking
  onLeaveReview: (target: ReviewTarget) => void
}

function BookingRow({ booking, onLeaveReview }: BookingRowProps) {
  return (
    <li className="flex items-center gap-4 p-4 border-b border-ink-100 last:border-b-0">
      <div className="w-11 h-11 bg-oxblood-50 rounded-xl flex items-center justify-center flex-shrink-0">
        <Video className="w-5 h-5 text-oxblood-700" aria-hidden="true" />
      </div>

      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2 mb-0.5">
          {/*
            `Booking` carries `advisorUsername` alongside `advisorId`, so the row
            links straight to the public profile route. No id-based fallback is
            needed and no cross-service username lookup is involved.
          */}
          <Link
            to={`/advisor/${booking.advisorUsername}`}
            className="text-sm font-semibold text-ink-900 truncate hover:text-oxblood-700 transition-colors"
          >
            {booking.advisorUsername}
          </Link>
          <Badge variant={BOOKING_STATUS_VARIANT[booking.status]}>
            {BOOKING_STATUS_LABEL[booking.status]}
          </Badge>
        </div>
        <p className="text-xs text-ink-400">
          {formatSessionDate(booking.sessionDate)} · {formatSessionTime(booking.sessionDate)} ·{' '}
          {booking.durationMinutes} min
        </p>
      </div>

      <div className="flex items-center gap-3 flex-shrink-0">
        {booking.status === 'COMPLETED' && (
          <Button
            variant="outline"
            size="sm"
            onClick={() =>
              onLeaveReview({
                id: booking.id,
                advisorId: booking.advisorId,
                advisorUsername: booking.advisorUsername,
              })
            }
          >
            Leave a review
          </Button>
        )}
        <span className="font-bold text-pine-600">
          {formatBookingAmount(booking.amountCharged)}
        </span>
      </div>
    </li>
  )
}

interface BookingSectionProps {
  title: string
  bookings: Booking[]
  emptyMessage: string
  onLeaveReview: (target: ReviewTarget) => void
}

function BookingSection({ title, bookings, emptyMessage, onLeaveReview }: BookingSectionProps) {
  return (
    <section className="mb-8">
      <h2 className="font-heading font-semibold text-ink-900 mb-3">
        {title} <span className="text-ink-400 font-normal">({bookings.length})</span>
      </h2>
      <div className="bg-white rounded-xl border border-ink-200 overflow-hidden">
        {bookings.length === 0 ? (
          <p className="px-4 py-8 text-sm text-ink-400 text-center">{emptyMessage}</p>
        ) : (
          <ul>
            {bookings.map((booking) => (
              <BookingRow key={booking.id} booking={booking} onLeaveReview={onLeaveReview} />
            ))}
          </ul>
        )}
      </div>
    </section>
  )
}

/**
 * The signed-in person's own sessions, at `/bookings`.
 *
 * Top-level rather than nested under `/dashboard` because both sides of a
 * booking need it: `/dashboard` is advisor-only, and a regular user has no
 * other place to see what they've booked.
 *
 * Review CTAs are gated on `status === 'COMPLETED'` and nothing else. The
 * `Booking` contract exposes no review-linkage field, so the client cannot tell
 * an already-reviewed session from a fresh one; the backend rejects the second
 * review for a booking, which is the real guard. Hiding the button behind a
 * guess we can't make would be worse than occasionally offering one that the
 * server declines.
 */
export function MyBookingsPage() {
  const [reviewTarget, setReviewTarget] = useState<ReviewTarget | null>(null)
  const now = useNow()
  const { data, isLoading, isError, error, refetch } = useMyBookings()

  const bookings = useMemo(() => data ?? [], [data])

  const upcoming = useMemo(
    () => bookings.filter((b) => isUpcoming(b, now)).sort(bySessionDateAscending),
    [bookings, now],
  )

  // Everything that isn't upcoming is past — complement rather than a second
  // predicate, so no booking can fall through both filters and vanish.
  const past = useMemo(
    () => bookings.filter((b) => !isUpcoming(b, now)).sort(bySessionDateDescending),
    [bookings, now],
  )

  function renderBody() {
    if (isLoading) {
      return (
        <div
          className="bg-white rounded-xl border border-ink-200 overflow-hidden"
          role="status"
          aria-label="Loading bookings"
        >
          {Array.from({ length: 3 }, (_, i) => (
            <BookingRowSkeleton key={i} />
          ))}
        </div>
      )
    }

    if (isError) {
      return <ErrorBanner message={getErrorMessage(error)} onRetry={() => void refetch()} />
    }

    if (bookings.length === 0) {
      return (
        <EmptyState
          icon={<CalendarClock className="w-8 h-8" />}
          title="No bookings yet"
          description="Once you book a session it will show up here."
          action={
            <Link to="/explore">
              <Button variant="primary">Explore advisors</Button>
            </Link>
          }
        />
      )
    }

    return (
      <>
        <BookingSection
          title="Upcoming"
          bookings={upcoming}
          emptyMessage="No upcoming sessions."
          onLeaveReview={setReviewTarget}
        />
        <BookingSection
          title="Past"
          bookings={past}
          emptyMessage="No past sessions."
          onLeaveReview={setReviewTarget}
        />
      </>
    )
  }

  return (
    <div className="min-h-screen bg-ink-50">
      <Navbar />
      <div className="pt-16 max-w-4xl mx-auto px-4 py-8">
        <h1 className="font-heading font-medium text-2xl text-ink-900 mb-6">My Bookings</h1>
        {renderBody()}
      </div>

      {reviewTarget && (
        <ReviewModal
          advisorId={reviewTarget.advisorId}
          bookingId={reviewTarget.id}
          advisorUsername={reviewTarget.advisorUsername}
          onClose={() => setReviewTarget(null)}
        />
      )}
    </div>
  )
}
