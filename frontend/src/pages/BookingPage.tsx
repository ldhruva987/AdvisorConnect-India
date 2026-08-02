import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  addMonths,
  eachDayOfInterval,
  endOfMonth,
  format,
  getDay,
  isBefore,
  isSameDay,
  isSameMonth,
  parseISO,
  startOfDay,
  startOfMonth,
} from 'date-fns'
import { loadStripe } from '@stripe/stripe-js'
import { Elements, PaymentElement, useElements, useStripe } from '@stripe/react-stripe-js'
import { ArrowLeft, CheckCircle, ChevronLeft, ChevronRight, Clock, Lock } from 'lucide-react'
import { Avatar } from '@/shared/components/ui/Avatar'
import { Badge } from '@/shared/components/ui/Badge'
import { Button } from '@/shared/components/ui/Button'
import { EmptyState } from '@/shared/components/ui/EmptyState'
import { ErrorBanner } from '@/shared/components/ui/ErrorBanner'
import { Skeleton } from '@/shared/components/ui/Skeleton'
import { Modal } from '@/shared/components/ui/Modal'
import { Navbar } from '@/shared/components/layout/Navbar'
import { useAdvisors } from '@/features/explore/hooks/useAdvisors'
import { useAvailability } from '@/features/booking/hooks/useAvailability'
import { useCreateBooking } from '@/features/booking/hooks/useCreateBooking'
import { useCreatePaymentIntent } from '@/features/booking/hooks/useCreatePaymentIntent'
import { getErrorMessage } from '@/lib/getErrorMessage'
import type { Booking, SessionDuration } from '@/types'

const DAY_HEADERS = ['Su', 'Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa']

const DURATION_OPTIONS: { duration: SessionDuration; price: number; label: string; popular?: boolean }[] = [
  { duration: 30, price: 39, label: '30 minutes' },
  { duration: 60, price: 69, label: '60 minutes', popular: true },
]

/**
 * Loaded once at module scope, per Stripe's guidance — `loadStripe` injects a
 * script tag, and calling it per render would re-run that on every re-render.
 *
 * Guarded on the key being present rather than called unconditionally:
 * `loadStripe(undefined)` throws at import time, which would take down the
 * whole route (and every test importing it) on a machine without an env file.
 */
const STRIPE_PUBLISHABLE_KEY = import.meta.env.VITE_STRIPE_PUBLISHABLE_KEY as string | undefined
const stripePromise = STRIPE_PUBLISHABLE_KEY ? loadStripe(STRIPE_PUBLISHABLE_KEY) : null

/** An ISO instant from the availability API, as a local wall-clock time. */
function formatSlotTime(iso: string): string {
  const date = new Date(iso)
  return Number.isNaN(date.getTime()) ? iso : format(date, 'h:mm a')
}

/* -------------------------------------------------------------------------- */
/* Payment                                                                    */
/* -------------------------------------------------------------------------- */

interface PaymentProps {
  advisorId: string
  durationMinutes: SessionDuration
  /** ISO instant of the chosen slot. */
  slot: string
  /** Display price for the button copy. Not server-driven yet — see the plan. */
  price: number
  onBooked: (booking: Booking) => void
}

/**
 * The `<PaymentElement>` half, which must live inside `<Elements>` so
 * `useStripe`/`useElements` resolve.
 *
 * Confirms the payment first and only then creates the booking: a booking row
 * that exists without a successful charge is the failure mode worth avoiding,
 * and it is the one ordering the backend's PENDING → CONFIRMED webhook flow
 * expects.
 */
function PaymentForm({ advisorId, durationMinutes, slot, price, onBooked }: PaymentProps) {
  const stripe = useStripe()
  const elements = useElements()
  const createBooking = useCreateBooking()
  const [confirming, setConfirming] = useState(false)
  const [paymentError, setPaymentError] = useState<string | null>(null)

  const handleConfirmPay = async () => {
    if (!stripe || !elements) return

    setPaymentError(null)
    setConfirming(true)
    try {
      const result = await stripe.confirmPayment({
        elements,
        confirmParams: { return_url: window.location.href },
        // Keeps the common card case on this page. Only payment methods that
        // genuinely require a redirect (iDEAL, some 3DS flows) leave it.
        redirect: 'if_required',
      })

      if (result.error) {
        /**
         * Stripe errors are plain objects, not `Error` instances, so
         * `getErrorMessage` would fall through to its generic copy and discard
         * the one genuinely useful sentence. Stripe's own `.message` is
         * already user-facing, so it wins; `getErrorMessage` is the fallback.
         */
        setPaymentError(result.error.message ?? getErrorMessage(result.error))
        return
      }

      const intent = result.paymentIntent
      if (!intent) {
        setPaymentError('Payment could not be confirmed. Please try again.')
        return
      }

      /**
       * The backend's `CreateBookingRequest.stripePaymentMethodId` is
       * `@NotBlank`. `payment_method` is a bare id string when the intent
       * isn't expanded; the intent id is the last-resort fallback so a
       * confirmed payment is never lost to a missing field.
       */
      const paymentMethodId =
        typeof intent.payment_method === 'string'
          ? intent.payment_method
          : (intent.payment_method?.id ?? intent.id)

      const created = await createBooking.mutateAsync({
        advisorId,
        sessionDateTime: slot,
        durationMinutes,
        stripePaymentMethodId: paymentMethodId,
      })
      onBooked(created.booking)
    } catch (err) {
      // Booking creation failed *after* a successful charge — an Axios error,
      // so the shared formatter is the right one here.
      setPaymentError(getErrorMessage(err))
    } finally {
      setConfirming(false)
    }
  }

  return (
    <>
      <PaymentElement />

      {paymentError && <ErrorBanner className="mt-4" message={paymentError} />}

      <Button
        className="mt-5"
        variant="primary"
        size="lg"
        fullWidth
        loading={confirming}
        disabled={!stripe || !elements}
        onClick={() => void handleConfirmPay()}
      >
        <Lock className="w-4 h-4" />
        Confirm &amp; Pay ${price}
      </Button>
      <p className="text-xs text-ink-400 text-center mt-3 flex items-center justify-center gap-1">
        <Lock className="w-3 h-3" />
        Secured by 256-bit SSL encryption. Your card is never stored.
      </p>
    </>
  )
}

/**
 * Owns the PaymentIntent, then hands its `clientSecret` to `<Elements>`.
 *
 * The intent is created here rather than inside `<Elements>` because
 * `options.clientSecret` is read when the Elements group is constructed and
 * cannot be introduced afterwards — a child cannot supply the secret its own
 * provider needed. Re-selecting a duration or slot mints a fresh intent (the
 * amount or time changed), and `key={clientSecret}` forces a clean remount so
 * no stale card state is confirmed against the new intent.
 */
function PaymentSection(props: PaymentProps) {
  const { advisorId, durationMinutes, slot } = props
  const { mutate, data, isPending, error } = useCreatePaymentIntent()

  useEffect(() => {
    mutate({ advisorId, durationMinutes, slot })
  }, [mutate, advisorId, durationMinutes, slot])

  if (!STRIPE_PUBLISHABLE_KEY) {
    return (
      <ErrorBanner message="Payments are unavailable: VITE_STRIPE_PUBLISHABLE_KEY is not configured." />
    )
  }

  if (error) {
    return <ErrorBanner message={getErrorMessage(error)} />
  }

  if (isPending || !data) {
    return (
      <div className="space-y-3" role="status" aria-label="Preparing secure payment">
        <Skeleton className="h-10 w-full rounded-lg" />
        <Skeleton className="h-10 w-full rounded-lg" />
        <Skeleton className="h-12 w-full rounded-lg" />
      </div>
    )
  }

  return (
    <Elements key={data.clientSecret} stripe={stripePromise} options={{ clientSecret: data.clientSecret }}>
      <PaymentForm {...props} />
    </Elements>
  )
}

/* -------------------------------------------------------------------------- */
/* Page                                                                       */
/* -------------------------------------------------------------------------- */

export function BookingPage() {
  const navigate = useNavigate()
  /**
   * Mounted at `/book/:advisorId`. Every caller (`AdvisorCard`, ChatPage) puts
   * an advisor **id** here, and the booking endpoints are id-keyed, so this is
   * the id — not the username.
   */
  const { advisorId } = useParams<{ advisorId: string }>()

  const [selectedDuration, setSelectedDuration] = useState<SessionDuration>(30)
  const [visibleMonth, setVisibleMonth] = useState(() => startOfMonth(new Date()))
  const [selectedDay, setSelectedDay] = useState<Date | null>(null)
  const [selectedSlot, setSelectedSlot] = useState<string | null>(null)
  const [createdBooking, setCreatedBooking] = useState<Booking | null>(null)

  /**
   * There is no advisor-by-id endpoint: `GET /advisors/{username}` is strictly
   * username-keyed on the backend, so `useAdvisorProfile` cannot resolve the id
   * this route carries. The search endpoint is the only real source that
   * returns ids, so the advisor is picked out of it. Purely cosmetic — the
   * booking itself only ever needs the id from the URL — so a miss degrades to
   * a neutral header rather than blocking the flow.
   */
  const { advisors, isLoading: advisorsLoading, errorMessage: advisorsError } = useAdvisors()
  const advisor = useMemo(
    () => advisors.find((a) => a.id === advisorId) ?? null,
    [advisors, advisorId],
  )

  const selectedDurationData = DURATION_OPTIONS.find((d) => d.duration === selectedDuration)!

  // ── Real calendar ────────────────────────────────────────────────────────
  const today = startOfDay(new Date())
  const monthStart = startOfMonth(visibleMonth)
  const monthDays = useMemo(
    () => eachDayOfInterval({ start: startOfMonth(visibleMonth), end: endOfMonth(visibleMonth) }),
    [visibleMonth],
  )
  /** Blank cells so the 1st lands under its real weekday column. */
  const leadingBlanks = getDay(monthStart)
  /** Nothing bookable exists in the past, so don't offer to navigate there. */
  const canGoPrev = !isSameMonth(monthStart, today)

  const dateStr = selectedDay ? format(selectedDay, 'yyyy-MM-dd') : undefined
  const availability = useAvailability(advisorId, dateStr)
  const availabilityError = availability.error ? getErrorMessage(availability.error) : null

  const selectDay = (day: Date) => {
    setSelectedDay(day)
    // The old slot belongs to a different date; keeping it would let a user
    // pay for a time they can no longer see.
    setSelectedSlot(null)
  }

  const changeMonth = (delta: number) => {
    setVisibleMonth((m) => startOfMonth(addMonths(m, delta)))
    setSelectedDay(null)
    setSelectedSlot(null)
  }

  if (!advisorId) {
    return (
      <div className="min-h-screen bg-ink-50">
        <Navbar />
        <div className="pt-16 max-w-2xl mx-auto px-4 py-8">
          <EmptyState
            title="No advisor selected"
            description="Pick an advisor from Explore to book a session."
            action={<Button onClick={() => navigate('/explore')}>Browse advisors</Button>}
          />
        </div>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-ink-50">
      <Navbar />
      <div className="pt-16 max-w-2xl mx-auto px-4 py-8">
        <button
          onClick={() => navigate(-1)}
          className="flex items-center gap-2 text-ink-500 hover:text-ink-900 transition-colors text-sm font-medium mb-6"
        >
          <ArrowLeft className="w-4 h-4" />
          Back
        </button>

        <h1 className="font-heading font-medium text-2xl text-ink-900 mb-6">Book a Video Session</h1>

        {/* Advisor summary */}
        <div className="bg-white rounded-xl border border-ink-200 p-5 mb-6 flex items-center gap-4">
          {advisorsLoading ? (
            <>
              <Skeleton className="w-14 h-14 rounded-full" />
              <div className="flex-1 space-y-2">
                <Skeleton className="h-4 w-1/3" />
                <Skeleton className="h-3 w-1/2" />
              </div>
            </>
          ) : (
            <>
              <Avatar username={advisor?.username ?? advisorId} color={advisor?.color} size="lg" />
              <div>
                <h2 className="font-heading font-semibold text-ink-900">
                  {advisor?.username ?? 'Advisor'}
                </h2>
                <p className="text-sm text-ink-500">{advisor?.title ?? 'Verified advisor'}</p>
              </div>
            </>
          )}
        </div>

        {advisorsError && <ErrorBanner className="mb-6" message={advisorsError} />}

        {/* Duration selector */}
        <div className="bg-white rounded-xl border border-ink-200 p-5 mb-6">
          <h3 className="font-heading font-semibold text-ink-900 mb-4 flex items-center gap-2">
            <Clock className="w-4 h-4 text-oxblood-700" />
            Select Duration
          </h3>
          <div className="grid grid-cols-2 gap-3">
            {DURATION_OPTIONS.map((opt) => (
              <button
                key={opt.duration}
                onClick={() => setSelectedDuration(opt.duration)}
                aria-pressed={selectedDuration === opt.duration}
                className={`relative p-4 rounded-xl border-2 text-left transition-all ${
                  selectedDuration === opt.duration
                    ? 'border-oxblood-700 bg-oxblood-50'
                    : 'border-ink-200 hover:border-ink-300'
                }`}
              >
                {opt.popular && (
                  <Badge variant="brand" className="absolute top-2 right-2">
                    POPULAR
                  </Badge>
                )}
                <p className="font-heading font-medium text-2xl text-ink-900">${opt.price}</p>
                <p className="text-sm text-ink-500">{opt.label}</p>
              </button>
            ))}
          </div>
        </div>

        {/* Calendar */}
        <div className="bg-white rounded-xl border border-ink-200 p-5 mb-6">
          <div className="flex items-center justify-between mb-4">
            <h3 className="font-heading font-semibold text-ink-900">
              Select Date — {format(visibleMonth, 'MMMM yyyy')}
            </h3>
            <div className="flex items-center gap-1">
              <button
                type="button"
                aria-label="Previous month"
                disabled={!canGoPrev}
                onClick={() => changeMonth(-1)}
                className="w-8 h-8 rounded-lg flex items-center justify-center text-ink-600 hover:bg-ink-100 disabled:opacity-30 disabled:cursor-not-allowed"
              >
                <ChevronLeft className="w-4 h-4" />
              </button>
              <button
                type="button"
                aria-label="Next month"
                onClick={() => changeMonth(1)}
                className="w-8 h-8 rounded-lg flex items-center justify-center text-ink-600 hover:bg-ink-100"
              >
                <ChevronRight className="w-4 h-4" />
              </button>
            </div>
          </div>

          <div className="grid grid-cols-7 mb-2">
            {DAY_HEADERS.map((d) => (
              <div key={d} className="text-center text-xs font-semibold text-ink-400 py-1">
                {d}
              </div>
            ))}
          </div>

          <div className="grid grid-cols-7 gap-1">
            {Array.from({ length: leadingBlanks }).map((_, i) => (
              <div key={`empty-${i}`} />
            ))}
            {monthDays.map((day) => {
              const disabled = isBefore(day, today)
              const selected = !!selectedDay && isSameDay(day, selectedDay)
              return (
                <button
                  key={day.toISOString()}
                  type="button"
                  disabled={disabled}
                  aria-label={format(day, 'EEEE, MMMM d, yyyy')}
                  aria-pressed={selected}
                  onClick={() => selectDay(day)}
                  className={`aspect-square flex items-center justify-center text-sm rounded-lg font-medium transition-all ${
                    disabled
                      ? 'text-ink-300 cursor-not-allowed'
                      : selected
                        ? 'bg-oxblood-700 text-white'
                        : 'text-ink-700 hover:bg-oxblood-50 hover:text-oxblood-700'
                  }`}
                >
                  {format(day, 'd')}
                </button>
              )
            })}
          </div>
        </div>

        {/* Time slots */}
        {selectedDay && (
          <div className="bg-white rounded-xl border border-ink-200 p-5 mb-6">
            <h3 className="font-heading font-semibold text-ink-900 mb-4">
              Select Time — {format(selectedDay, 'MMMM d, yyyy')}
            </h3>

            {availabilityError ? (
              <ErrorBanner message={availabilityError} onRetry={() => void availability.refetch()} />
            ) : availability.isLoading ? (
              <div className="grid grid-cols-4 gap-2" role="status" aria-label="Loading available times">
                {Array.from({ length: 8 }).map((_, i) => (
                  <Skeleton key={i} className="h-10 rounded-lg" />
                ))}
              </div>
            ) : (availability.data?.length ?? 0) === 0 ? (
              <EmptyState
                className="py-8"
                title="No times available"
                description="This advisor has nothing free on that day. Try another date."
              />
            ) : (
              <div className="grid grid-cols-4 gap-2">
                {/* The API returns only what IS free — there is no "taken" slot
                    to render, so none is invented. */}
                {availability.data?.map((slot) => (
                  <button
                    key={slot}
                    type="button"
                    aria-pressed={selectedSlot === slot}
                    onClick={() => setSelectedSlot(slot)}
                    className={`py-2.5 px-2 rounded-lg text-xs font-medium border transition-all ${
                      selectedSlot === slot
                        ? 'bg-oxblood-700 text-white border-oxblood-700'
                        : 'border-ink-200 text-ink-700 hover:border-oxblood-700 hover:text-oxblood-700'
                    }`}
                  >
                    {formatSlotTime(slot)}
                  </button>
                ))}
              </div>
            )}
          </div>
        )}

        {/* Order summary */}
        <div className="bg-white rounded-xl border border-ink-200 p-5 mb-6">
          <h3 className="font-heading font-semibold text-ink-900 mb-3">Order Summary</h3>
          <div className="space-y-2 text-sm">
            <div className="flex justify-between text-ink-700">
              <span>Video session ({selectedDurationData.duration} min)</span>
              <span className="font-semibold">${selectedDurationData.price}</span>
            </div>
            <div className="flex justify-between text-ink-500">
              <span>Platform fee</span>
              <span className="text-pine-600 font-medium">$0 free</span>
            </div>
            <div className="border-t border-ink-100 pt-2 flex justify-between font-heading font-medium text-ink-900 text-base">
              <span>Total</span>
              <span>${selectedDurationData.price}</span>
            </div>
          </div>
        </div>

        {/* Payment */}
        <div className="bg-white rounded-xl border border-ink-200 p-5 mb-6">
          <h3 className="font-heading font-semibold text-ink-900 mb-4">Payment Details</h3>
          {selectedSlot ? (
            <PaymentSection
              advisorId={advisorId}
              durationMinutes={selectedDuration}
              slot={selectedSlot}
              price={selectedDurationData.price}
              onBooked={setCreatedBooking}
            />
          ) : (
            <p className="text-sm text-ink-500">Choose a date and time to continue to payment.</p>
          )}
        </div>
      </div>

      {/* Confirmation modal — driven by the booking the server actually created. */}
      <Modal
        open={!!createdBooking}
        onClose={() => {
          setCreatedBooking(null)
          navigate('/explore')
        }}
      >
        {createdBooking && (
          <div className="text-center">
            <div className="w-16 h-16 bg-pine-100 rounded-full flex items-center justify-center mx-auto mb-4">
              <CheckCircle className="w-8 h-8 text-pine-600" />
            </div>
            <h2 className="font-heading font-medium text-2xl text-ink-900 mb-2">Booking Confirmed!</h2>
            <p className="text-ink-500 mb-1">
              Your {createdBooking.durationMinutes}-minute video session with{' '}
              <span className="font-semibold text-ink-900">
                {createdBooking.advisorUsername || advisor?.username || 'your advisor'}
              </span>
            </p>
            <p className="text-oxblood-700 font-semibold mb-6">
              {format(parseISO(createdBooking.sessionDate), "EEEE, MMMM d, yyyy · h:mm a")}
            </p>
            <p className="text-sm text-ink-400 mb-6">
              You'll receive a calendar invite and video link via email. You can reschedule up to 2 hours
              before your session.
            </p>
            <Button
              variant="primary"
              fullWidth
              onClick={() => {
                setCreatedBooking(null)
                navigate('/explore')
              }}
            >
              Back to Home
            </Button>
          </div>
        )}
      </Modal>
    </div>
  )
}
