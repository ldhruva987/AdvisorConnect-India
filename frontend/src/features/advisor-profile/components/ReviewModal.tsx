import { useState } from 'react'
import { Star } from 'lucide-react'
import { Button } from '@/shared/components/ui/Button'
import { ErrorBanner } from '@/shared/components/ui/ErrorBanner'
import { Textarea } from '@/shared/components/ui/Input'
import { Modal } from '@/shared/components/ui/Modal'
import { cn } from '@/lib/utils'
import { getErrorMessage } from '@/lib/getErrorMessage'
import { toast } from '@/stores/toastStore'
import { useSubmitReview } from '../hooks/useSubmitReview'

const RATING_VALUES = [1, 2, 3, 4, 5] as const

/** Backend caps review bodies; mirrored here so the user sees the limit first. */
export const REVIEW_TEXT_MAX_LENGTH = 1000

interface StarPickerProps {
  value: number
  onChange: (rating: number) => void
  disabled?: boolean
}

/**
 * Inline 1–5 star picker. Not extracted to the shared UI kit: this is the only
 * rating *input* in the app (every other star on screen is a read-only display
 * built from `Array.from({ length: rating })`), and a one-consumer abstraction
 * would be indirection without reuse.
 *
 * Implemented as a radio group rather than five buttons so keyboard users get
 * arrow-key traversal for free and screen readers announce "3 of 5 selected"
 * instead of five unrelated toggles.
 */
function StarPicker({ value, onChange, disabled }: StarPickerProps) {
  // Hover preview is separate from the committed value so moving the mouse away
  // restores the real selection rather than leaving the preview stuck on.
  const [hovered, setHovered] = useState<number | null>(null)
  const shown = hovered ?? value

  return (
    <div
      role="radiogroup"
      aria-label="Rating"
      className="flex items-center gap-1"
      onMouseLeave={() => setHovered(null)}
    >
      {RATING_VALUES.map((rating) => {
        const filled = rating <= shown
        return (
          <button
            key={rating}
            type="button"
            role="radio"
            aria-checked={value === rating}
            aria-label={`${rating} ${rating === 1 ? 'star' : 'stars'}`}
            disabled={disabled}
            onClick={() => onChange(rating)}
            onMouseEnter={() => setHovered(rating)}
            onFocus={() => setHovered(rating)}
            onBlur={() => setHovered(null)}
            className="p-1 rounded-md transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-oxblood-600 disabled:cursor-not-allowed"
          >
            <Star
              className={cn(
                'w-7 h-7 transition-colors',
                filled ? 'text-warn-500 fill-warn-500' : 'text-ink-300',
              )}
            />
          </button>
        )
      })}
    </div>
  )
}

export interface ReviewModalProps {
  advisorId: string
  /**
   * The completed booking being reviewed. Required by the backend, which gates
   * creation on a completed, not-yet-reviewed booking between the two parties.
   */
  bookingId: string
  /** Shown in the heading when the caller knows it. */
  advisorUsername?: string
  onClose: () => void
  /** Fired after a successful submit, before `onClose`. */
  onSuccess?: () => void
}

/**
 * "Leave a review" dialog, opened from MyBookingsPage's completed sessions and
 * from AdvisorProfilePage when the viewer has an eligible completed booking.
 *
 * Mount it conditionally — it renders an always-open `Modal` rather than taking
 * an `open` prop, so unmounting is what discards a half-written draft. That is
 * the behaviour we want: reopening should start clean, not resurrect an
 * abandoned rating.
 *
 * Cache invalidation lives in `useSubmitReview` (both the advisor's review list
 * and the profile's `averageRating`), so there is none here.
 */
export function ReviewModal({
  advisorId,
  bookingId,
  advisorUsername,
  onClose,
  onSuccess,
}: ReviewModalProps) {
  const [rating, setRating] = useState(0)
  const [text, setText] = useState('')
  const submitReview = useSubmitReview(advisorId)

  const canSubmit = rating > 0 && !submitReview.isPending

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    if (!canSubmit) return

    submitReview.mutate(
      { bookingId, rating, text: text.trim() },
      {
        onSuccess: () => {
          toast.success('Thanks — your review has been posted.')
          onSuccess?.()
          onClose()
        },
        // Failure deliberately leaves the modal open with the draft intact:
        // the common causes (network blip, a duplicate-review rejection worth
        // reading) are all things the user needs the form still on screen for.
      },
    )
  }

  return (
    <Modal open onClose={onClose}>
      <form onSubmit={handleSubmit} className="space-y-5">
        <div>
          <h2 className="font-heading font-medium text-xl text-ink-900">Leave a review</h2>
          <p className="text-sm text-ink-500 mt-1">
            {advisorUsername
              ? `How was your session with ${advisorUsername}?`
              : 'How was your session?'}
          </p>
        </div>

        {submitReview.isError && <ErrorBanner message={getErrorMessage(submitReview.error)} />}

        <div className="space-y-1.5">
          <span className="block text-sm font-medium text-ink-700">
            Rating<span className="text-danger-600 ml-0.5">*</span>
          </span>
          <StarPicker value={rating} onChange={setRating} disabled={submitReview.isPending} />
        </div>

        <Textarea
          label="Your review"
          id="review-text"
          rows={4}
          maxLength={REVIEW_TEXT_MAX_LENGTH}
          placeholder="What stood out? What would you tell someone considering a session?"
          value={text}
          disabled={submitReview.isPending}
          onChange={(e) => setText(e.target.value)}
        />

        <div className="flex gap-3 justify-end">
          <Button type="button" variant="ghost" onClick={onClose} disabled={submitReview.isPending}>
            Cancel
          </Button>
          <Button type="submit" variant="primary" loading={submitReview.isPending} disabled={rating === 0}>
            Submit review
          </Button>
        </div>
      </form>
    </Modal>
  )
}
