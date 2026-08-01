import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ArrowLeft, Lock, CheckCircle, Clock } from 'lucide-react'
import { Avatar } from '@/shared/components/ui/Avatar'
import { Badge } from '@/shared/components/ui/Badge'
import { Button } from '@/shared/components/ui/Button'
import { Input } from '@/shared/components/ui/Input'
import { Modal } from '@/shared/components/ui/Modal'
import { Navbar } from '@/shared/components/layout/Navbar'
import type { SessionDuration, TimeSlot } from '@/types'

const MOCK_ADVISOR = {
  id: '1',
  username: '@MindfulRohan',
  title: 'Licensed Clinical Psychologist',
  color: '#8a3f24',
}

const DAY_HEADERS = ['Su', 'Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa']

// March 2026 starts on Sunday (0)
const MARCH_DAYS = Array.from({ length: 31 }, (_, i) => i + 1)
// Days 1-16 are disabled, 17-31 are selectable
const DISABLED_DAYS = new Set([1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16])

// March 1 2026 is a Sunday, so offset = 0
const START_OFFSET = 0

const TIME_SLOTS: TimeSlot[] = [
  { time: '9:00 AM',  taken: false },
  { time: '9:30 AM',  taken: true  },
  { time: '10:00 AM', taken: false },
  { time: '10:30 AM', taken: false },
  { time: '11:00 AM', taken: true  },
  { time: '11:30 AM', taken: false },
  { time: '1:00 PM',  taken: false },
  { time: '1:30 PM',  taken: true  },
  { time: '2:00 PM',  taken: false },
  { time: '2:30 PM',  taken: false },
  { time: '3:00 PM',  taken: true  },
  { time: '4:00 PM',  taken: false },
]

const DURATION_OPTIONS: { duration: SessionDuration; price: number; label: string; popular?: boolean }[] = [
  { duration: 30, price: 39, label: '30 minutes' },
  { duration: 60, price: 69, label: '60 minutes', popular: true },
]

export function BookingPage() {
  const navigate = useNavigate()
  const [selectedDuration, setSelectedDuration] = useState<SessionDuration>(30)
  const [selectedDay, setSelectedDay] = useState<number | null>(null)
  const [selectedTime, setSelectedTime] = useState<string | null>(null)
  const [cardName, setCardName] = useState('')
  const [cardNumber, setCardNumber] = useState('')
  const [expiry, setExpiry] = useState('')
  const [cvc, setCvc] = useState('')
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [loading, setLoading] = useState(false)

  const selectedDurationData = DURATION_OPTIONS.find((d) => d.duration === selectedDuration)!

  const handleConfirmPay = () => {
    setLoading(true)
    setTimeout(() => {
      setLoading(false)
      setConfirmOpen(true)
    }, 1200)
  }

  const formatCardNumber = (value: string) => {
    return value.replace(/\D/g, '').slice(0, 16).replace(/(.{4})/g, '$1 ').trim()
  }

  const formatExpiry = (value: string) => {
    const digits = value.replace(/\D/g, '').slice(0, 4)
    return digits.length >= 2 ? `${digits.slice(0, 2)}/${digits.slice(2)}` : digits
  }

  return (
    <div className="min-h-screen bg-ink-50">
      <Navbar />
      <div className="pt-16 max-w-2xl mx-auto px-4 py-8">
        {/* Back */}
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
          <Avatar username={MOCK_ADVISOR.username} color={MOCK_ADVISOR.color} size="lg" />
          <div>
            <h2 className="font-heading font-semibold text-ink-900">{MOCK_ADVISOR.username}</h2>
            <p className="text-sm text-ink-500">{MOCK_ADVISOR.title}</p>
          </div>
        </div>

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
                className={`relative p-4 rounded-xl border-2 text-left transition-all ${
                  selectedDuration === opt.duration
                    ? 'border-oxblood-700 bg-oxblood-50'
                    : 'border-ink-200 hover:border-ink-300'
                }`}
              >
                {opt.popular && (
                  <Badge variant="brand" className="absolute top-2 right-2">POPULAR</Badge>
                )}
                <p className="font-heading font-medium text-2xl text-ink-900">${opt.price}</p>
                <p className="text-sm text-ink-500">{opt.label}</p>
              </button>
            ))}
          </div>
        </div>

        {/* Calendar */}
        <div className="bg-white rounded-xl border border-ink-200 p-5 mb-6">
          <h3 className="font-heading font-semibold text-ink-900 mb-4">Select Date — March 2026</h3>
          {/* Day headers */}
          <div className="grid grid-cols-7 mb-2">
            {DAY_HEADERS.map((d) => (
              <div key={d} className="text-center text-xs font-semibold text-ink-400 py-1">{d}</div>
            ))}
          </div>
          {/* Days grid */}
          <div className="grid grid-cols-7 gap-1">
            {Array.from({ length: START_OFFSET }).map((_, i) => (
              <div key={`empty-${i}`} />
            ))}
            {MARCH_DAYS.map((day) => {
              const disabled = DISABLED_DAYS.has(day)
              const selected = selectedDay === day
              return (
                <button
                  key={day}
                  disabled={disabled}
                  onClick={() => { setSelectedDay(day); setSelectedTime(null) }}
                  className={`aspect-square flex items-center justify-center text-sm rounded-lg font-medium transition-all ${
                    disabled
                      ? 'text-ink-300 cursor-not-allowed'
                      : selected
                        ? 'bg-oxblood-700 text-white'
                        : 'text-ink-700 hover:bg-oxblood-50 hover:text-oxblood-700'
                  }`}
                >
                  {day}
                </button>
              )
            })}
          </div>
        </div>

        {/* Time slots */}
        {selectedDay && (
          <div className="bg-white rounded-xl border border-ink-200 p-5 mb-6">
            <h3 className="font-heading font-semibold text-ink-900 mb-4">
              Select Time — March {selectedDay}, 2026
            </h3>
            <div className="grid grid-cols-4 gap-2">
              {TIME_SLOTS.map((slot) => (
                <button
                  key={slot.time}
                  disabled={slot.taken}
                  onClick={() => setSelectedTime(slot.time)}
                  className={`py-2.5 px-2 rounded-lg text-xs font-medium border transition-all ${
                    slot.taken
                      ? 'border-ink-100 text-ink-300 cursor-not-allowed bg-ink-50'
                      : selectedTime === slot.time
                        ? 'bg-oxblood-700 text-white border-oxblood-700'
                        : 'border-ink-200 text-ink-700 hover:border-oxblood-700 hover:text-oxblood-700'
                  }`}
                >
                  {slot.taken ? (
                    <span className="text-ink-300">Taken</span>
                  ) : (
                    slot.time
                  )}
                </button>
              ))}
            </div>
          </div>
        )}

        {/* Payment form */}
        <div className="bg-white rounded-xl border border-ink-200 p-5 mb-6">
          <h3 className="font-heading font-semibold text-ink-900 mb-4">Payment Details</h3>
          <div className="bg-ink-50 rounded-xl p-4 space-y-4">
            <Input
              label="Name on card"
              placeholder="John Smith"
              value={cardName}
              onChange={(e) => setCardName(e.target.value)}
            />
            <Input
              label="Card number"
              placeholder="4242 4242 4242 4242"
              value={cardNumber}
              onChange={(e) => setCardNumber(formatCardNumber(e.target.value))}
              maxLength={19}
            />
            <div className="grid grid-cols-2 gap-3">
              <Input
                label="Expiry"
                placeholder="MM/YY"
                value={expiry}
                onChange={(e) => setExpiry(formatExpiry(e.target.value))}
                maxLength={5}
              />
              <Input
                label="CVC"
                placeholder="123"
                value={cvc}
                onChange={(e) => setCvc(e.target.value.replace(/\D/g, '').slice(0, 3))}
                maxLength={3}
              />
            </div>
          </div>
        </div>

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

        {/* Confirm button */}
        <Button
          variant="primary"
          size="lg"
          fullWidth
          loading={loading}
          onClick={handleConfirmPay}
          disabled={!selectedDay || !selectedTime || !cardName || !cardNumber}
        >
          <Lock className="w-4 h-4" />
          Confirm & Pay ${selectedDurationData.price}
        </Button>
        <p className="text-xs text-ink-400 text-center mt-3 flex items-center justify-center gap-1">
          <Lock className="w-3 h-3" />
          Secured by 256-bit SSL encryption. Your card is never stored.
        </p>
      </div>

      {/* Confirmation modal */}
      <Modal open={confirmOpen} onClose={() => { setConfirmOpen(false); navigate('/explore') }}>
        <div className="text-center">
          <div className="w-16 h-16 bg-pine-100 rounded-full flex items-center justify-center mx-auto mb-4">
            <CheckCircle className="w-8 h-8 text-pine-600" />
          </div>
          <h2 className="font-heading font-medium text-2xl text-ink-900 mb-2">Booking Confirmed!</h2>
          <p className="text-ink-500 mb-1">
            Your video session with <span className="font-semibold text-ink-900">{MOCK_ADVISOR.username}</span>
          </p>
          {selectedDay && selectedTime && (
            <p className="text-oxblood-700 font-semibold mb-6">
              March {selectedDay}, 2026 · {selectedTime}
            </p>
          )}
          <p className="text-sm text-ink-400 mb-6">
            You'll receive a calendar invite and video link via email. You can reschedule up to 2 hours before your session.
          </p>
          <Button variant="primary" fullWidth onClick={() => { setConfirmOpen(false); navigate('/explore') }}>
            Back to Home
          </Button>
        </div>
      </Modal>
    </div>
  )
}
