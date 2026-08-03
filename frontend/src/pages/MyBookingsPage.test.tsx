import { describe, expect, it } from 'vitest'
import { delay, http, HttpResponse } from 'msw'
import userEvent from '@testing-library/user-event'
import { server } from '@/test/mocks/server'
import { render, screen, waitFor, within } from '@/test/test-utils'
import type { Booking } from '@/types'
import { MyBookingsPage } from './MyBookingsPage'

const DAY_MS = 24 * 60 * 60 * 1000

/**
 * Fixtures are built relative to `Date.now()` rather than reusing the
 * fixed-date `MOCK_BOOKINGS`. The upcoming/past split compares against the wall
 * clock, so a hardcoded 2026 instant would silently reclassify itself — and
 * turn a green suite red — the moment that date passes.
 */
function bookingAt(offsetDays: number, overrides: Partial<Booking> = {}): Booking {
  return {
    id: `booking-${offsetDays}`,
    advisorId: 'advisor-1',
    advisorUsername: 'maya_chen',
    sessionDate: new Date(Date.now() + offsetDays * DAY_MS).toISOString(),
    durationMinutes: 30,
    amountCharged: 500,
    status: 'CONFIRMED',
    createdAt: new Date(Date.now() - 10 * DAY_MS).toISOString(),
    ...overrides,
  }
}

function serveBookings(bookings: Booking[]) {
  server.use(http.get('*/api/bookings/me', () => HttpResponse.json(bookings)))
}

/** Scope queries to one of the two sections so a hit can't come from the other. */
function section(name: RegExp) {
  const heading = screen.getByRole('heading', { name })
  const element = heading.closest('section')
  if (!element) throw new Error(`No <section> wrapping heading ${name}`)
  return within(element)
}

describe('MyBookingsPage', () => {
  describe('loading', () => {
    it('shows skeleton rows while the request is in flight', async () => {
      server.use(
        http.get('*/api/bookings/me', async () => {
          await delay(50)
          return HttpResponse.json([])
        }),
      )
      render(<MyBookingsPage />)

      expect(screen.getByRole('status', { name: 'Loading bookings' })).toBeInTheDocument()
      // Neither real content nor the empty state may appear before data lands.
      expect(screen.queryByText('No bookings yet')).not.toBeInTheDocument()

      await waitFor(() =>
        expect(screen.queryByRole('status', { name: 'Loading bookings' })).not.toBeInTheDocument(),
      )
    })
  })

  describe('upcoming vs past', () => {
    it('files future sessions under Upcoming and finished ones under Past', async () => {
      serveBookings([
        bookingAt(3, { id: 'future', advisorUsername: 'maya_chen' }),
        bookingAt(-4, { id: 'done', advisorUsername: 'sam_okafor', status: 'COMPLETED' }),
      ])
      render(<MyBookingsPage />)

      await screen.findByText('maya_chen')

      expect(section(/^Upcoming/).getByText('maya_chen')).toBeInTheDocument()
      expect(section(/^Upcoming/).queryByText('sam_okafor')).not.toBeInTheDocument()
      expect(section(/^Past/).getByText('sam_okafor')).toBeInTheDocument()
      expect(section(/^Past/).queryByText('maya_chen')).not.toBeInTheDocument()
    })

    /**
     * The two statuses that make a session history regardless of its clock
     * time. A cancelled session sitting in "Upcoming" would read as still
     * happening.
     */
    it('treats cancelled and completed future sessions as past', async () => {
      serveBookings([
        bookingAt(2, { id: 'cancelled', advisorUsername: 'riley_stone', status: 'CANCELLED' }),
        bookingAt(6, { id: 'done_future', advisorUsername: 'dana_wu', status: 'COMPLETED' }),
        bookingAt(1, { id: 'real', advisorUsername: 'maya_chen', status: 'CONFIRMED' }),
      ])
      render(<MyBookingsPage />)

      await screen.findByText('maya_chen')

      expect(section(/^Upcoming/).queryByText('riley_stone')).not.toBeInTheDocument()
      expect(section(/^Upcoming/).queryByText('dana_wu')).not.toBeInTheDocument()
      expect(section(/^Past/).getByText('riley_stone')).toBeInTheDocument()
      expect(section(/^Past/).getByText('dana_wu')).toBeInTheDocument()
    })

    it('orders upcoming soonest-first and past most-recent-first', async () => {
      serveBookings([
        bookingAt(9, { id: 'later', advisorUsername: 'later_advisor' }),
        bookingAt(2, { id: 'sooner', advisorUsername: 'sooner_advisor' }),
        bookingAt(-9, { id: 'oldest', advisorUsername: 'oldest_advisor', status: 'COMPLETED' }),
        bookingAt(-2, { id: 'recent', advisorUsername: 'recent_advisor', status: 'COMPLETED' }),
      ])
      render(<MyBookingsPage />)

      await screen.findByText('sooner_advisor')

      const upcoming = section(/^Upcoming/).getAllByRole('link')
      expect(upcoming.map((a) => a.textContent)).toEqual(['sooner_advisor', 'later_advisor'])

      const past = section(/^Past/).getAllByRole('link')
      expect(past.map((a) => a.textContent)).toEqual(['recent_advisor', 'oldest_advisor'])
    })

    it('counts each section in its heading', async () => {
      serveBookings([
        bookingAt(3, { id: 'a' }),
        bookingAt(5, { id: 'b' }),
        bookingAt(-1, { id: 'c', status: 'COMPLETED' }),
      ])
      render(<MyBookingsPage />)

      expect(await screen.findByRole('heading', { name: 'Upcoming (2)' })).toBeInTheDocument()
      expect(screen.getByRole('heading', { name: 'Past (1)' })).toBeInTheDocument()
    })

    it('tells the user a section is empty rather than dropping it', async () => {
      serveBookings([bookingAt(3, { id: 'only-upcoming' })])
      render(<MyBookingsPage />)

      await screen.findByRole('heading', { name: 'Upcoming (1)' })
      expect(section(/^Past/).getByText('No past sessions.')).toBeInTheDocument()
    })
  })

  describe('rows', () => {
    /**
     * `Booking` carries `advisorUsername`, so the row can link straight to the
     * public profile route — no id-based fallback and no username lookup.
     */
    it('links the advisor to their profile by username', async () => {
      serveBookings([bookingAt(3, { advisorUsername: 'sam_okafor' })])
      render(<MyBookingsPage />)

      const link = await screen.findByRole('link', { name: 'sam_okafor' })
      expect(link).toHaveAttribute('href', '/advisor/sam_okafor')
    })

    it('renders duration, status and the charge in major units', async () => {
      serveBookings([
        bookingAt(3, { durationMinutes: 60, amountCharged: 900, status: 'PENDING' }),
      ])
      render(<MyBookingsPage />)

      await screen.findByText('maya_chen')
      expect(screen.getByText(/60 min/)).toBeInTheDocument()
      expect(screen.getByText('Pending')).toBeInTheDocument()
      // `amountCharged` is already a major-unit rupee decimal, not paise — ₹900.00, not ₹90,000.
      expect(screen.getByText('₹900.00')).toBeInTheDocument()
    })
  })

  describe('empty', () => {
    it('shows an EmptyState with a route into Explore', async () => {
      serveBookings([])
      render(<MyBookingsPage />)

      expect(await screen.findByText('No bookings yet')).toBeInTheDocument()
      // Queried as a button, not a link: the Navbar also has an "Explore
      // Advisors" link to `/explore`, and only the EmptyState CTA is a button.
      const cta = screen.getByRole('button', { name: /explore advisors/i })
      expect(cta.closest('a')).toHaveAttribute('href', '/explore')
      // No section scaffolding when there is nothing at all to file.
      expect(screen.queryByRole('heading', { name: /^Upcoming/ })).not.toBeInTheDocument()
    })
  })

  describe('error', () => {
    it('shows an ErrorBanner whose retry refetches', async () => {
      let attempt = 0
      server.use(
        http.get('*/api/bookings/me', () => {
          attempt += 1
          if (attempt === 1) return new HttpResponse(null, { status: 500 })
          return HttpResponse.json([bookingAt(3, { advisorUsername: 'maya_chen' })])
        }),
      )
      const user = userEvent.setup()
      render(<MyBookingsPage />)

      const banner = await screen.findByRole('alert')
      expect(banner).toHaveTextContent('Something went wrong. Please try again.')

      await user.click(within(banner).getByRole('button', { name: /try again/i }))

      expect(await screen.findByText('maya_chen')).toBeInTheDocument()
      expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    })
  })

  describe('leave a review', () => {
    it('offers the CTA on completed sessions only', async () => {
      serveBookings([
        bookingAt(-3, { id: 'done', advisorUsername: 'sam_okafor', status: 'COMPLETED' }),
        bookingAt(-4, { id: 'scrapped', advisorUsername: 'riley_stone', status: 'CANCELLED' }),
        bookingAt(3, { id: 'ahead', advisorUsername: 'maya_chen', status: 'CONFIRMED' }),
      ])
      render(<MyBookingsPage />)

      await screen.findByText('sam_okafor')

      // Exactly one CTA across the whole page...
      expect(screen.getAllByRole('button', { name: /leave a review/i })).toHaveLength(1)

      // ...and it is the completed row's, not the cancelled or upcoming one's.
      const rowFor = (username: string) => {
        const row = screen
          .getAllByRole('listitem')
          .find((item) => within(item).queryByText(username))
        if (!row) throw new Error(`No booking row for ${username}`)
        return within(row)
      }

      expect(rowFor('sam_okafor').getByRole('button', { name: /leave a review/i })).toBeInTheDocument()
      expect(rowFor('riley_stone').queryByRole('button', { name: /leave a review/i })).toBeNull()
      expect(rowFor('maya_chen').queryByRole('button', { name: /leave a review/i })).toBeNull()
    })

    it('opens ReviewModal for the booking whose CTA was clicked', async () => {
      serveBookings([
        bookingAt(-3, { id: 'booking-done', advisorUsername: 'sam_okafor', status: 'COMPLETED' }),
      ])
      const user = userEvent.setup()
      render(<MyBookingsPage />)

      await screen.findByText('sam_okafor')
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()

      await user.click(screen.getByRole('button', { name: /leave a review/i }))

      const dialog = screen.getByRole('dialog')
      expect(within(dialog).getByRole('heading', { name: 'Leave a review' })).toBeInTheDocument()
      expect(within(dialog).getByText(/session with sam_okafor/)).toBeInTheDocument()
      expect(within(dialog).getAllByRole('radio')).toHaveLength(5)
    })

    it('submits the clicked booking id and closes on success', async () => {
      serveBookings([
        bookingAt(-3, { id: 'booking-done', advisorId: 'advisor-2', advisorUsername: 'sam_okafor', status: 'COMPLETED' }),
      ])
      const submitted: Array<{ advisorId: string; bookingId: string }> = []
      server.use(
        http.post('*/api/advisors/:advisorId/reviews', async ({ request, params }) => {
          const body = (await request.json()) as { bookingId: string }
          submitted.push({ advisorId: String(params.advisorId), bookingId: body.bookingId })
          return HttpResponse.json({ id: 'review-new' }, { status: 201 })
        }),
      )
      const user = userEvent.setup()
      render(<MyBookingsPage />)

      await screen.findByText('sam_okafor')
      await user.click(screen.getByRole('button', { name: /leave a review/i }))
      await user.click(within(screen.getByRole('dialog')).getByRole('radio', { name: '5 stars' }))
      await user.click(screen.getByRole('button', { name: /submit review/i }))

      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
      expect(submitted).toEqual([{ advisorId: 'advisor-2', bookingId: 'booking-done' }])
    })

    it('closes the modal on Cancel without leaving it mounted', async () => {
      serveBookings([bookingAt(-3, { advisorUsername: 'sam_okafor', status: 'COMPLETED' })])
      const user = userEvent.setup()
      render(<MyBookingsPage />)

      await screen.findByText('sam_okafor')
      await user.click(screen.getByRole('button', { name: /leave a review/i }))
      await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: /cancel/i }))

      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    })
  })
})
