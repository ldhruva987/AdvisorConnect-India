import type { ReactNode } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import userEvent from '@testing-library/user-event'
import { format } from 'date-fns'
import { server } from '@/test/mocks/server'
import {
  MOCK_AVAILABILITY_DATE,
  MOCK_AVAILABLE_SLOTS,
  MOCK_CREATED_BOOKING,
} from '@/test/mocks/handlers/bookings'
import { render, screen, waitFor, within } from '@/test/test-utils'
import type { Booking } from '@/types'
import type { CreateBookingRequest, CreatePaymentIntentRequest } from '@/types/api'
import { BookingPage } from './BookingPage'

/* -------------------------------------------------------------------------- */
/* Stripe double                                                              */
/* -------------------------------------------------------------------------- */

/**
 * Everything in here has to exist before `./BookingPage` is evaluated, which is
 * why it lives in `vi.hoisted`:
 *
 *  - the publishable key is read at module scope, and without it the page
 *    short-circuits to a "payments are unavailable" banner and none of the
 *    payment flow is reachable;
 *  - `loadStripe` is called at module scope too.
 *
 * `@stripe/react-stripe-js` is mocked rather than driven for real because
 * `<PaymentElement>` is a cross-origin iframe: there is nothing in jsdom to
 * type a card number into. What this file can and does verify is the contract
 * the page owns — that it confirms the payment *before* creating the booking,
 * passes the right identifiers, and handles both outcomes.
 */
const stripeDouble = vi.hoisted(() => {
  vi.stubEnv('VITE_STRIPE_PUBLISHABLE_KEY', 'pk_test_booking_page')

  const confirmPayment = vi.fn()
  return {
    confirmPayment,
    instance: { confirmPayment },
    /** Flipped off to exercise the "Stripe hasn't initialised yet" branch. */
    ready: true,
  }
})

vi.mock('@stripe/stripe-js', () => ({
  // The real implementation injects a <script> tag at import time.
  loadStripe: vi.fn(() => Promise.resolve(null)),
}))

vi.mock('@stripe/react-stripe-js', () => ({
  Elements: ({ children }: { children: ReactNode }) => (
    <div data-testid="stripe-elements">{children}</div>
  ),
  PaymentElement: () => <div data-testid="payment-element" />,
  useStripe: () => (stripeDouble.ready ? stripeDouble.instance : null),
  useElements: () => (stripeDouble.ready ? {} : null),
}))

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
          ...MOCK_CREATED_BOOKING,
          advisorId: body.advisorId,
          sessionDate: body.sessionDateTime,
          durationMinutes: body.durationMinutes,
        } satisfies Booking,
        { status: 201 },
      )
    }),
  )
  return bodies
}

/**
 * Drives the page to the point where `<PaymentElement>` is mounted: advisor
 * loaded → day picked → slot picked → PaymentIntent created.
 */
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
  await screen.findByTestId('payment-element')

  return result
}

const payButton = () => screen.getByRole('button', { name: /confirm & pay/i })

beforeEach(() => {
  // Only `Date` is faked. Leaving `setTimeout` real keeps MSW, react-query and
  // userEvent on their own clocks — faking those as well deadlocks them.
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(FROZEN_NOW)

  stripeDouble.ready = true
  stripeDouble.confirmPayment.mockReset()
  stripeDouble.confirmPayment.mockResolvedValue({
    paymentIntent: { id: 'pi_test_1', payment_method: 'pm_test_1', status: 'succeeded' },
  })
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

      expect(screen.queryByTestId('payment-element')).not.toBeInTheDocument()
      expect(screen.getByText('Choose a date and time to continue to payment.')).toBeInTheDocument()
    })
  })

  describe('payment intent', () => {
    it('is created for the advisor, duration and slot on screen', async () => {
      const intents: CreatePaymentIntentRequest[] = []
      server.use(
        http.post('*/api/bookings/payment-intent', async ({ request }) => {
          intents.push((await request.json()) as CreatePaymentIntentRequest)
          return HttpResponse.json({ clientSecret: 'pi_test_secret_123', amount: 9000 })
        }),
      )
      const user = userEvent.setup()
      await reachPayment(user, { duration: 60 })

      expect(intents).toEqual([
        { advisorId: 'advisor-1', durationMinutes: 60, slot: FIRST_SLOT },
      ])
    })

    it('holds the payment form back until the intent resolves', async () => {
      // Gated rather than timed: the default handler answers within the same
      // tick as the click, so the loading state would never be observable.
      let release!: () => void
      const gate = new Promise<void>((resolve) => {
        release = resolve
      })
      server.use(
        http.post('*/api/bookings/payment-intent', async () => {
          await gate
          return HttpResponse.json({ clientSecret: 'pi_test_secret_123', amount: 4500 })
        }),
      )
      const user = userEvent.setup()
      renderBooking()
      await screen.findByRole('heading', { name: 'Select Date — August 2026' })
      await user.click(screen.getByRole('button', { name: SLOT_DAY_LABEL }))
      await user.click(await screen.findByRole('button', { name: slotLabel(FIRST_SLOT) }))

      expect(screen.getByRole('status', { name: /preparing secure payment/i })).toBeInTheDocument()
      expect(screen.queryByTestId('payment-element')).not.toBeInTheDocument()

      release()
      expect(await screen.findByTestId('payment-element')).toBeInTheDocument()
    })

    it('surfaces an intent failure instead of a dead form', async () => {
      server.use(
        http.post('*/api/bookings/payment-intent', () => new HttpResponse(null, { status: 500 })),
      )
      const user = userEvent.setup()
      renderBooking()
      await screen.findByRole('heading', { name: 'Select Date — August 2026' })
      await user.click(screen.getByRole('button', { name: SLOT_DAY_LABEL }))
      await user.click(await screen.findByRole('button', { name: slotLabel(FIRST_SLOT) }))

      expect(await screen.findByRole('alert')).toHaveTextContent(
        'Something went wrong. Please try again.',
      )
      expect(screen.queryByTestId('payment-element')).not.toBeInTheDocument()
    })

    it('prices the button from the chosen duration', async () => {
      const user = userEvent.setup()
      await reachPayment(user)
      expect(screen.getByRole('button', { name: /confirm & pay \$39/i })).toBeInTheDocument()

      await user.click(screen.getByRole('button', { name: /60 minutes/ }))
      expect(await screen.findByRole('button', { name: /confirm & pay \$69/i })).toBeInTheDocument()
    })
  })

  describe('successful payment', () => {
    it('confirms with Stripe, then creates the booking with the real selection', async () => {
      const bookings = captureBookings()
      const user = userEvent.setup()
      await reachPayment(user)

      await user.click(payButton())

      await screen.findByText('Booking Confirmed!')
      expect(stripeDouble.confirmPayment).toHaveBeenCalledTimes(1)
      expect(stripeDouble.confirmPayment.mock.calls[0][0]).toMatchObject({
        redirect: 'if_required',
      })
      expect(bookings).toEqual([
        {
          advisorId: 'advisor-1',
          sessionDateTime: FIRST_SLOT,
          durationMinutes: 30,
          // Taken off the confirmed intent, not invented client-side.
          stripePaymentMethodId: 'pm_test_1',
        },
      ])
    })

    it('carries the 60-minute selection through to the booking', async () => {
      const bookings = captureBookings()
      const user = userEvent.setup()
      await reachPayment(user, { duration: 60 })

      await user.click(payButton())

      await screen.findByText('Booking Confirmed!')
      expect(bookings[0].durationMinutes).toBe(60)
    })

    it('falls back to the intent id when no payment method is expanded', async () => {
      stripeDouble.confirmPayment.mockResolvedValue({
        paymentIntent: { id: 'pi_only', status: 'succeeded' },
      })
      const bookings = captureBookings()
      const user = userEvent.setup()
      await reachPayment(user)

      await user.click(payButton())

      await screen.findByText('Booking Confirmed!')
      // The backend marks `stripePaymentMethodId` @NotBlank — a confirmed
      // charge must never be lost to an empty field.
      expect(bookings[0].stripePaymentMethodId).toBe('pi_only')
    })

    it('confirms the modal with the booking the server returned', async () => {
      captureBookings()
      const user = userEvent.setup()
      await reachPayment(user)

      await user.click(payButton())

      const dialog = await screen.findByRole('dialog')
      expect(within(dialog).getByText('Booking Confirmed!')).toBeInTheDocument()
      expect(dialog).toHaveTextContent('Your 30-minute video session with maya_chen')
      expect(
        within(dialog).getByText(format(new Date(FIRST_SLOT), "EEEE, MMMM d, yyyy · h:mm a")),
      ).toBeInTheDocument()
      // Nothing from the old fixed-March confirmation copy.
      expect(dialog.textContent ?? '').not.toMatch(/March/)
    })

    it('disables the button and shows the loading state while confirming', async () => {
      let release: ((value: unknown) => void) | undefined
      stripeDouble.confirmPayment.mockImplementation(
        () =>
          new Promise((resolve) => {
            release = resolve
          }),
      )
      captureBookings()
      const user = userEvent.setup()
      await reachPayment(user)

      const button = payButton()
      await user.click(button)

      // `Button` disables itself while `loading`, which is what stops a
      // double-click charging the card twice.
      await waitFor(() => expect(button).toBeDisabled())
      expect(button.querySelector('.animate-spin')).not.toBeNull()

      release!({ paymentIntent: { id: 'pi_test_1', payment_method: 'pm_test_1' } })
      expect(await screen.findByText('Booking Confirmed!')).toBeInTheDocument()
    })
  })

  describe('failed payment', () => {
    it('shows Stripe’s own message and creates no booking', async () => {
      stripeDouble.confirmPayment.mockResolvedValue({
        error: { type: 'card_error', code: 'card_declined', message: 'Your card was declined.' },
      })
      const bookings = captureBookings()
      const user = userEvent.setup()
      await reachPayment(user)

      await user.click(payButton())

      expect(await screen.findByRole('alert')).toHaveTextContent('Your card was declined.')
      expect(bookings).toEqual([])
      expect(screen.queryByText('Booking Confirmed!')).not.toBeInTheDocument()
      // Still recoverable — the form stays up so the user can try another card.
      expect(screen.getByTestId('payment-element')).toBeInTheDocument()
      await waitFor(() => expect(payButton()).toBeEnabled())
    })

    it('falls back to generic copy when Stripe supplies no message', async () => {
      stripeDouble.confirmPayment.mockResolvedValue({ error: { type: 'api_error' } })
      const bookings = captureBookings()
      const user = userEvent.setup()
      await reachPayment(user)

      await user.click(payButton())

      expect(await screen.findByRole('alert')).toHaveTextContent(
        'Something went wrong. Please try again.',
      )
      expect(bookings).toEqual([])
    })

    it('reports a confirmation that returns neither an error nor an intent', async () => {
      stripeDouble.confirmPayment.mockResolvedValue({})
      const bookings = captureBookings()
      const user = userEvent.setup()
      await reachPayment(user)

      await user.click(payButton())

      expect(await screen.findByRole('alert')).toHaveTextContent(
        'Payment could not be confirmed. Please try again.',
      )
      expect(bookings).toEqual([])
    })

    it('reports a booking that fails after the charge succeeded', async () => {
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
    })

    it('keeps the button disabled until Stripe has initialised', async () => {
      stripeDouble.ready = false
      const user = userEvent.setup()
      await reachPayment(user)

      expect(payButton()).toBeDisabled()
      expect(stripeDouble.confirmPayment).not.toHaveBeenCalled()
    })
  })
})
