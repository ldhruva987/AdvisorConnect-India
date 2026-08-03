import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import userEvent from '@testing-library/user-event'
import { format } from 'date-fns'
import { server } from '@/test/mocks/server'
import {
  MOCK_AVAILABILITY_DATE,
  MOCK_AVAILABLE_SLOTS,
  MOCK_CREATED_BOOKING,
  MOCK_RAZORPAY_ORDER_ID,
} from '@/test/mocks/handlers/bookings'
import { render, screen, waitFor, within } from '@/test/test-utils'
import type { CreateBookingRequest, CreateBookingResponse } from '@/types/api'
import type { RazorpayCheckoutOptions, RazorpayPaymentResponse } from '@/lib/razorpay'
import { BookingPage } from './BookingPage'

/* -------------------------------------------------------------------------- */
/* Razorpay double                                                            */
/* -------------------------------------------------------------------------- */

/**
 * Everything in here has to exist before `./BookingPage` is evaluated, which is
 * why it lives in `vi.hoisted`:
 *
 *  - the key id is read at module scope, and without it the page short-circuits
 *    to a "payments are unavailable" banner and none of the payment flow is
 *    reachable;
 *  - `@/lib/razorpay` is mocked rather than driven for real because Razorpay's
 *    Checkout is a real hosted modal loaded from an external script — there is
 *    nothing in jsdom to render it or type a card number into. What this file
 *    can and does verify is the contract the page owns: that it creates the
 *    booking first, opens Checkout with the order id the backend returned, and
 *    handles both a successful payment and a dismissed modal correctly.
 */
const razorpayDouble = vi.hoisted(() => {
  vi.stubEnv('VITE_RAZORPAY_KEY_ID', 'rzp_test_booking_page')

  return {
    load: vi.fn(() => Promise.resolve()),
    open: vi.fn(),
    /** The options passed to the most recent openRazorpayCheckout() call. */
    lastOptions: undefined as RazorpayCheckoutOptions | undefined,
  }
})

vi.mock('@/lib/razorpay', () => ({
  loadRazorpayCheckout: () => razorpayDouble.load(),
  openRazorpayCheckout: (options: RazorpayCheckoutOptions) => {
    razorpayDouble.lastOptions = options
    razorpayDouble.open(options)
    return { open: vi.fn() }
  },
}))

/** Fires the `handler` the page registered, as Checkout would on a successful payment. */
function completeCheckoutPayment() {
  const response: RazorpayPaymentResponse = {
    razorpay_payment_id: 'pay_test_1',
    razorpay_order_id: MOCK_RAZORPAY_ORDER_ID,
    razorpay_signature: 'sig_test_1',
  }
  razorpayDouble.lastOptions?.handler(response)
}

/** Fires the `modal.ondismiss` the page registered, as Checkout would on a closed modal. */
function dismissCheckout() {
  razorpayDouble.lastOptions?.modal?.ondismiss?.()
}

/* -------------------------------------------------------------------------- */
/* Fixtures & helpers                                                         */
/* -------------------------------------------------------------------------- */

/**
 * Constructed from local parts rather than an ISO instant so the frozen day is
 * "10 August 2026" in every timezone a CI box might run in. A `Z` instant would
 * land on the 9th or the 11th at the edges and take the calendar assertions
 * with it.
 */
const FROZEN_NOW = new Date(2026, 7, 10, 12, 0, 0)

/** The fixture availability date (`2026-08-14`) as the calendar labels it. */
const SLOT_DAY_LABEL = 'Friday, August 14, 2026'
/** A day the fixture has no availability for. */
const EMPTY_DAY_LABEL = 'Saturday, August 15, 2026'

/**
 * Slot labels are derived, never hardcoded: the page renders ISO instants as
 * local wall-clock times, so a literal "2:30 PM" would only be correct in one
 * timezone.
 */
const slotLabel = (iso: string) => format(new Date(iso), 'h:mm a')
const FIRST_SLOT = MOCK_AVAILABLE_SLOTS[0]

function renderBooking(advisorId = 'advisor-1') {
  return render(<BookingPage />, {
    initialEntries: [`/book/${advisorId}`],
    path: '/book/:advisorId',
  })
}

/** Collects every `POST /bookings` body, so "no booking was created" is provable. */
function captureBookings(): CreateBookingRequest[] {
  const bodies: CreateBookingRequest[] = []
  server.use(
    http.post('*/api/bookings', async ({ request }) => {
      const body = (await request.json()) as CreateBookingRequest
      bodies.push(body)
      return HttpResponse.json(
        {
          booking: {
            ...MOCK_CREATED_BOOKING,
            advisorId: body.advisorId,
            sessionDate: body.sessionDateTime,
            durationMinutes: body.durationMinutes,
          },
          razorpayOrderId: MOCK_RAZORPAY_ORDER_ID,
        } satisfies CreateBookingResponse,
        { status: 201 },
      )
    }),
  )
  return bodies
}

/** Drives the page to the point where the pay button is available: advisor loaded → day picked → slot picked. */
async function reachPayment(
  user: ReturnType<typeof userEvent.setup>,
  options: { advisorId?: string; duration?: 30 | 60 } = {},
) {
  const { advisorId = 'advisor-1', duration } = options
  const result = renderBooking(advisorId)

  await screen.findByRole('heading', { level: 2, name: /maya_chen|sam_okafor|rio_alvarez|Advisor/ })
  if (duration === 60) {
    await user.click(screen.getByRole('button', { name: /60 minutes/ }))
  }

  await user.click(screen.getByRole('button', { name: SLOT_DAY_LABEL }))
  await user.click(await screen.findByRole('button', { name: slotLabel(FIRST_SLOT) }))
  await screen.findByRole('button', { name: /confirm & pay/i })

  return result
}

const payButton = () => screen.getByRole('button', { name: /confirm & pay|retry payment/i })

beforeEach(() => {
  // Only `Date` is faked. Leaving `setTimeout` real keeps MSW, react-query and
  // userEvent on their own clocks — faking those as well deadlocks them.
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(FROZEN_NOW)

  razorpayDouble.lastOptions = undefined
  razorpayDouble.load.mockClear()
  razorpayDouble.load.mockResolvedValue(undefined)
  razorpayDouble.open.mockClear()
})

afterEach(() => {
  vi.useRealTimers()
})

/* -------------------------------------------------------------------------- */
/* Tests                                                                      */
/* -------------------------------------------------------------------------- */

describe('BookingPage', () => {
  describe('regression: the calendar is a real calendar', () => {
    /**
     * The bug this covers: the page rendered a hand-built grid of 31 cells
     * captioned "March 2026", with days 1–16 hardcoded as unavailable. It said
     * March in August, and in every month after.
     */
    it('renders the month the clock is actually in', async () => {
      renderBooking()

      expect(
        await screen.findByRole('heading', { name: 'Select Date — August 2026' }),
      ).toBeInTheDocument()
      expect(document.body.textContent ?? '').not.toMatch(/March 2026/)
    })

    it('follows the clock into a different month', async () => {
      vi.setSystemTime(new Date(2026, 10, 3, 12, 0, 0))
      renderBooking()

      expect(
        await screen.findByRole('heading', { name: 'Select Date — November 2026' }),
      ).toBeInTheDocument()
      // 30 days in November, and no phantom 31st carried over from the old
      // fixed-length March grid.
      expect(screen.getByRole('button', { name: 'Monday, November 30, 2026' })).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: /November 31/ })).not.toBeInTheDocument()
    })

    it('aligns the 1st under its real weekday column', async () => {
      renderBooking()

      const first = await screen.findByRole('button', { name: 'Saturday, August 1, 2026' })
      // 1 August 2026 is a Saturday, so six blank cells must precede it.
      const cells = Array.from(first.parentElement!.children)
      expect(cells.indexOf(first)).toBe(6)
    })

    it('disables days in the past but not today', async () => {
      renderBooking()

      expect(await screen.findByRole('button', { name: 'Sunday, August 9, 2026' })).toBeDisabled()
      expect(screen.getByRole('button', { name: 'Monday, August 10, 2026' })).toBeEnabled()
      expect(screen.getByRole('button', { name: 'Tuesday, August 11, 2026' })).toBeEnabled()
    })

    it('refuses to navigate before the current month', async () => {
      const user = userEvent.setup()
      renderBooking()
      await screen.findByRole('heading', { name: 'Select Date — August 2026' })

      expect(screen.getByRole('button', { name: 'Previous month' })).toBeDisabled()

      await user.click(screen.getByRole('button', { name: 'Next month' }))
      expect(screen.getByRole('heading', { name: 'Select Date — September 2026' })).toBeInTheDocument()
      // Every day of a future month is bookable, including its 1st.
      expect(screen.getByRole('button', { name: 'Tuesday, September 1, 2026' })).toBeEnabled()

      const prev = screen.getByRole('button', { name: 'Previous month' })
      expect(prev).toBeEnabled()
      await user.click(prev)
      expect(screen.getByRole('heading', { name: 'Select Date — August 2026' })).toBeInTheDocument()
    })
  })

  describe('regression: the advisor comes from the URL', () => {
    it('renders the advisor whose id is in the path', async () => {
      renderBooking('advisor-2')

      expect(await screen.findByRole('heading', { level: 2, name: 'sam_okafor' })).toBeInTheDocument()
      expect(screen.getByText('Licensed Therapist')).toBeInTheDocument()
    })

    it('shows no trace of the old hardcoded advisor', async () => {
      renderBooking('advisor-1')
      await screen.findByRole('heading', { level: 2, name: 'maya_chen' })

      const text = document.body.textContent ?? ''
      expect(text).not.toContain('@MindfulRohan')
      expect(text).not.toContain('Licensed Clinical Psychologist')
    })

    it('degrades to neutral copy for an id the search endpoint does not know', async () => {
      renderBooking('advisor-unknown')

      expect(await screen.findByRole('heading', { level: 2, name: 'Advisor' })).toBeInTheDocument()
      expect(screen.getByText('Verified advisor')).toBeInTheDocument()
      // Cosmetic only — the booking flow itself must stay open.
      expect(screen.getByRole('heading', { name: 'Select Date — August 2026' })).toBeInTheDocument()
    })

    it('offers a way out when there is no advisor id at all', () => {
      render(<BookingPage />, { initialEntries: ['/book'], path: '/book' })

      expect(screen.getByText('No advisor selected')).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /browse advisors/i })).toBeInTheDocument()
    })
  })

  describe('availability', () => {
    it('fetches slots for the day that was clicked', async () => {
      const requests: { advisorId: string; date: string | null }[] = []
      server.use(
        http.get('*/api/bookings/availability/:advisorId', ({ params, request }) => {
          requests.push({
            advisorId: String(params.advisorId),
            date: new URL(request.url).searchParams.get('date'),
          })
          return HttpResponse.json(MOCK_AVAILABLE_SLOTS)
        }),
      )
      const user = userEvent.setup()
      renderBooking('advisor-1')
      await screen.findByRole('heading', { name: 'Select Date — August 2026' })

      await user.click(screen.getByRole('button', { name: SLOT_DAY_LABEL }))

      await waitFor(() =>
        expect(requests).toEqual([{ advisorId: 'advisor-1', date: MOCK_AVAILABILITY_DATE }]),
      )
    })

    it('renders only the slots the API returned', async () => {
      const user = userEvent.setup()
      renderBooking()
      await screen.findByRole('heading', { name: 'Select Date — August 2026' })

      await user.click(screen.getByRole('button', { name: SLOT_DAY_LABEL }))

      for (const iso of MOCK_AVAILABLE_SLOTS) {
        expect(await screen.findByRole('button', { name: slotLabel(iso) })).toBeEnabled()
      }
      expect(screen.getByRole('heading', { name: 'Select Time — August 14, 2026' })).toBeInTheDocument()
      // The old grid rendered eight fixed slots with three flagged `taken`; the
      // real contract only ever reports what is free.
      expect(screen.queryByText(/booked|unavailable/i)).not.toBeInTheDocument()
    })

    it('shows an empty state for a day with nothing free', async () => {
      const user = userEvent.setup()
      renderBooking()
      await screen.findByRole('heading', { name: 'Select Date — August 2026' })

      await user.click(screen.getByRole('button', { name: EMPTY_DAY_LABEL }))

      expect(await screen.findByText('No times available')).toBeInTheDocument()
    })

    it('shows an ErrorBanner with a working retry when availability fails', async () => {
      let attempt = 0
      server.use(
        http.get('*/api/bookings/availability/:advisorId', () => {
          attempt += 1
          if (attempt === 1) return new HttpResponse(null, { status: 500 })
          return HttpResponse.json(MOCK_AVAILABLE_SLOTS)
        }),
      )
      const user = userEvent.setup()
      renderBooking()
      await screen.findByRole('heading', { name: 'Select Date — August 2026' })
      await user.click(screen.getByRole('button', { name: SLOT_DAY_LABEL }))

      const banner = await screen.findByRole('alert')
      await user.click(within(banner).getByRole('button', { name: /try again/i }))

      expect(await screen.findByRole('button', { name: slotLabel(FIRST_SLOT) })).toBeInTheDocument()
    })

    it('drops the chosen slot when the day changes', async () => {
      const user = userEvent.setup()
      await reachPayment(user)

      await user.click(screen.getByRole('button', { name: EMPTY_DAY_LABEL }))

      expect(screen.queryByRole('button', { name: /confirm & pay/i })).not.toBeInTheDocument()
      expect(screen.getByText('Choose a date and time to continue to payment.')).toBeInTheDocument()
    })
  })

  describe('price', () => {
    it('prices the button from the chosen duration, matching booking-service PricingPolicy', async () => {
      const user = userEvent.setup()
      await reachPayment(user)
      expect(screen.getByRole('button', { name: /confirm & pay ₹500/i })).toBeInTheDocument()

      await user.click(screen.getByRole('button', { name: /60 minutes/ }))
      expect(await screen.findByRole('button', { name: /confirm & pay ₹900/i })).toBeInTheDocument()
    })

    it('shows an unavailable banner when no Razorpay key is configured', async () => {
      vi.stubEnv('VITE_RAZORPAY_KEY_ID', '')
      const user = userEvent.setup()
      renderBooking()
      await screen.findByRole('heading', { name: 'Select Date — August 2026' })
      await user.click(screen.getByRole('button', { name: SLOT_DAY_LABEL }))
      await user.click(await screen.findByRole('button', { name: slotLabel(FIRST_SLOT) }))

      expect(
        await screen.findByText(/payments are unavailable.*VITE_RAZORPAY_KEY_ID/i),
      ).toBeInTheDocument()
      vi.stubEnv('VITE_RAZORPAY_KEY_ID', 'rzp_test_booking_page')
    })
  })

  describe('successful payment', () => {
    it('creates the booking with the real selection, then opens Razorpay Checkout', async () => {
      const bookings = captureBookings()
      const user = userEvent.setup()
      await reachPayment(user)

      await user.click(payButton())

      await waitFor(() =>
        expect(bookings).toEqual([
          { advisorId: 'advisor-1', sessionDateTime: FIRST_SLOT, durationMinutes: 30 },
        ]),
      )
      await waitFor(() => expect(razorpayDouble.open).toHaveBeenCalledTimes(1))
      expect(razorpayDouble.lastOptions).toMatchObject({
        key: 'rzp_test_booking_page',
        order_id: MOCK_RAZORPAY_ORDER_ID,
      })
    })

    it('carries the 60-minute selection through to the booking', async () => {
      const bookings = captureBookings()
      const user = userEvent.setup()
      await reachPayment(user, { duration: 60 })

      await user.click(payButton())

      await waitFor(() => expect(bookings[0]?.durationMinutes).toBe(60))
    })

    it('confirms the modal once Checkout reports a successful payment', async () => {
      captureBookings()
      const user = userEvent.setup()
      await reachPayment(user)

      await user.click(payButton())
      await waitFor(() => expect(razorpayDouble.open).toHaveBeenCalledTimes(1))
      completeCheckoutPayment()

      const dialog = await screen.findByRole('dialog')
      expect(within(dialog).getByText('Booking Confirmed!')).toBeInTheDocument()
      expect(dialog).toHaveTextContent('Your 30-minute video session with maya_chen')
      // Nothing from the old fixed-March confirmation copy.
      expect(dialog.textContent ?? '').not.toMatch(/March/)
    })

    it('disables the button and shows the loading state while the booking is being created', async () => {
      let release!: () => void
      const gate = new Promise<void>((resolve) => {
        release = resolve
      })
      server.use(
        http.post('*/api/bookings', async () => {
          await gate
          return HttpResponse.json(
            { booking: MOCK_CREATED_BOOKING, razorpayOrderId: MOCK_RAZORPAY_ORDER_ID },
            { status: 201 },
          )
        }),
      )
      const user = userEvent.setup()
      await reachPayment(user)

      const button = payButton()
      await user.click(button)

      await waitFor(() => expect(button).toBeDisabled())
      expect(button.querySelector('.animate-spin')).not.toBeNull()

      release()
      await waitFor(() => expect(razorpayDouble.open).toHaveBeenCalledTimes(1))
    })
  })

  describe('dismissed payment', () => {
    it('lets the user retry without creating a second booking', async () => {
      const bookings = captureBookings()
      const user = userEvent.setup()
      await reachPayment(user)

      await user.click(payButton())
      await waitFor(() => expect(razorpayDouble.open).toHaveBeenCalledTimes(1))
      dismissCheckout()

      expect(
        await screen.findByText(/payment was not completed.*slot is still held/i),
      ).toBeInTheDocument()
      const retryButton = screen.getByRole('button', { name: /retry payment/i })

      await user.click(retryButton)

      // Reopens Checkout for the same order — no second POST /bookings.
      await waitFor(() => expect(razorpayDouble.open).toHaveBeenCalledTimes(2))
      expect(bookings).toHaveLength(1)
    })

    it('confirms the booking if the retried payment succeeds', async () => {
      captureBookings()
      const user = userEvent.setup()
      await reachPayment(user)

      await user.click(payButton())
      await waitFor(() => expect(razorpayDouble.open).toHaveBeenCalledTimes(1))
      dismissCheckout()
      await user.click(await screen.findByRole('button', { name: /retry payment/i }))
      await waitFor(() => expect(razorpayDouble.open).toHaveBeenCalledTimes(2))
      completeCheckoutPayment()

      expect(await screen.findByText('Booking Confirmed!')).toBeInTheDocument()
    })
  })

  describe('failed booking creation', () => {
    it('surfaces the backend message and never opens Checkout', async () => {
      server.use(
        http.post('*/api/bookings', () =>
          HttpResponse.json({ message: 'That slot was just taken.' }, { status: 409 }),
        ),
      )
      const user = userEvent.setup()
      await reachPayment(user)

      await user.click(payButton())

      expect(await screen.findByRole('alert')).toHaveTextContent('That slot was just taken.')
      expect(screen.queryByText('Booking Confirmed!')).not.toBeInTheDocument()
      expect(razorpayDouble.open).not.toHaveBeenCalled()
    })

    it('surfaces a Checkout script load failure without losing the booking', async () => {
      captureBookings()
      razorpayDouble.load.mockRejectedValueOnce(new Error('Could not load the Razorpay Checkout script.'))
      const user = userEvent.setup()
      await reachPayment(user)

      await user.click(payButton())

      expect(await screen.findByRole('alert')).toHaveTextContent(
        'Could not load the Razorpay Checkout script.',
      )
      expect(razorpayDouble.open).not.toHaveBeenCalled()
    })
  })
})
