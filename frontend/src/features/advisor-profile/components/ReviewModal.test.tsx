import { describe, expect, it, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import userEvent from '@testing-library/user-event'
import { server } from '@/test/mocks/server'
import { render, screen, waitFor } from '@/test/test-utils'
import { useToastStore } from '@/stores/toastStore'
import type { SubmitReviewRequest } from '@/types/api'
import { ReviewModal } from './ReviewModal'

/**
 * The modal is mounted conditionally by its callers rather than toggled with an
 * `open` prop, so every test here renders it already open — which is the only
 * state it has.
 */
function renderModal(overrides: Partial<React.ComponentProps<typeof ReviewModal>> = {}) {
  const onClose = vi.fn()
  const onSuccess = vi.fn()
  const result = render(
    <ReviewModal
      advisorId="advisor-1"
      bookingId="booking-2"
      advisorUsername="maya_chen"
      onClose={onClose}
      onSuccess={onSuccess}
      {...overrides}
    />,
  )
  return { ...result, onClose, onSuccess }
}

/** Install a review-submission handler and record the bodies it receives. */
function captureSubmissions(respond?: () => Response) {
  const bodies: SubmitReviewRequest[] = []
  server.use(
    http.post('*/api/advisors/:advisorId/reviews', async ({ request }) => {
      bodies.push((await request.json()) as SubmitReviewRequest)
      return respond
        ? respond()
        : HttpResponse.json({ id: 'review-new' }, { status: 201 })
    }),
  )
  return bodies
}

describe('ReviewModal', () => {
  it('names the advisor being reviewed', () => {
    renderModal()

    expect(screen.getByRole('heading', { name: 'Leave a review' })).toBeInTheDocument()
    expect(screen.getByText(/How was your session with maya_chen\?/)).toBeInTheDocument()
  })

  describe('star rating', () => {
    it('renders five stars, none selected initially', () => {
      renderModal()

      const stars = screen.getAllByRole('radio')
      expect(stars).toHaveLength(5)
      stars.forEach((star) => expect(star).toHaveAttribute('aria-checked', 'false'))
    })

    it('marks the clicked star as the selection', async () => {
      const user = userEvent.setup()
      renderModal()

      await user.click(screen.getByRole('radio', { name: '4 stars' }))

      expect(screen.getByRole('radio', { name: '4 stars' })).toHaveAttribute('aria-checked', 'true')
      expect(screen.getByRole('radio', { name: '3 stars' })).toHaveAttribute('aria-checked', 'false')
    })

    it('replaces the selection rather than accumulating it', async () => {
      const user = userEvent.setup()
      renderModal()

      await user.click(screen.getByRole('radio', { name: '5 stars' }))
      await user.click(screen.getByRole('radio', { name: '2 stars' }))

      expect(screen.getByRole('radio', { name: '2 stars' })).toHaveAttribute('aria-checked', 'true')
      expect(screen.getByRole('radio', { name: '5 stars' })).toHaveAttribute('aria-checked', 'false')
    })

    /** A review with no rating is not a review; the API would reject it anyway. */
    it('keeps submit disabled until a rating is chosen', async () => {
      const user = userEvent.setup()
      renderModal()

      expect(screen.getByRole('button', { name: /submit review/i })).toBeDisabled()

      await user.click(screen.getByRole('radio', { name: '1 star' }))

      expect(screen.getByRole('button', { name: /submit review/i })).toBeEnabled()
    })
  })

  describe('submitting', () => {
    it('posts the rating, text and booking id for this advisor', async () => {
      const bodies = captureSubmissions()
      const user = userEvent.setup()
      renderModal()

      await user.click(screen.getByRole('radio', { name: '4 stars' }))
      await user.type(screen.getByLabelText('Your review'), 'Practical and direct.')
      await user.click(screen.getByRole('button', { name: /submit review/i }))

      await waitFor(() => expect(bodies).toHaveLength(1))
      expect(bodies[0]).toEqual({
        bookingId: 'booking-2',
        rating: 4,
        text: 'Practical and direct.',
      })
    })

    it('trims the review text so whitespace-only padding never reaches the API', async () => {
      const bodies = captureSubmissions()
      const user = userEvent.setup()
      renderModal()

      await user.click(screen.getByRole('radio', { name: '5 stars' }))
      await user.type(screen.getByLabelText('Your review'), '   Great.   ')
      await user.click(screen.getByRole('button', { name: /submit review/i }))

      await waitFor(() => expect(bodies).toHaveLength(1))
      expect(bodies[0].text).toBe('Great.')
    })

    it('submits against the advisor id it was given', async () => {
      const advisorIds: string[] = []
      server.use(
        http.post('*/api/advisors/:advisorId/reviews', ({ params }) => {
          advisorIds.push(String(params.advisorId))
          return HttpResponse.json({ id: 'review-new' }, { status: 201 })
        }),
      )
      const user = userEvent.setup()
      renderModal({ advisorId: 'advisor-3' })

      await user.click(screen.getByRole('radio', { name: '3 stars' }))
      await user.click(screen.getByRole('button', { name: /submit review/i }))

      await waitFor(() => expect(advisorIds).toEqual(['advisor-3']))
    })

    it('toasts, fires onSuccess and closes on success', async () => {
      captureSubmissions()
      const user = userEvent.setup()
      const { onClose, onSuccess } = renderModal()

      await user.click(screen.getByRole('radio', { name: '5 stars' }))
      await user.click(screen.getByRole('button', { name: /submit review/i }))

      await waitFor(() => expect(onClose).toHaveBeenCalledTimes(1))
      expect(onSuccess).toHaveBeenCalledTimes(1)

      const toasts = useToastStore.getState().toasts
      expect(toasts).toHaveLength(1)
      expect(toasts[0].variant).toBe('success')
      expect(toasts[0].message).toMatch(/your review has been posted/i)
    })

    /**
     * The realistic failure here is the backend's duplicate-review rejection,
     * which is the safety net behind the CTA's optimistic client-side
     * eligibility guess — so its message has to reach the user, and the draft
     * has to survive.
     */
    it('shows the server message and stays open on failure', async () => {
      captureSubmissions(() =>
        HttpResponse.json({ message: 'You have already reviewed this session.' }, { status: 409 }),
      )
      const user = userEvent.setup()
      const { onClose, onSuccess } = renderModal()

      await user.click(screen.getByRole('radio', { name: '4 stars' }))
      await user.type(screen.getByLabelText('Your review'), 'Second attempt.')
      await user.click(screen.getByRole('button', { name: /submit review/i }))

      expect(await screen.findByRole('alert')).toHaveTextContent(
        'You have already reviewed this session.',
      )
      expect(onClose).not.toHaveBeenCalled()
      expect(onSuccess).not.toHaveBeenCalled()
      expect(useToastStore.getState().toasts).toHaveLength(0)
      // The draft is still there to retry or edit.
      expect(screen.getByLabelText('Your review')).toHaveValue('Second attempt.')
      expect(screen.getByRole('radio', { name: '4 stars' })).toHaveAttribute('aria-checked', 'true')
    })
  })

  describe('dismissing', () => {
    it('closes via Cancel without submitting', async () => {
      const bodies = captureSubmissions()
      const user = userEvent.setup()
      const { onClose } = renderModal()

      await user.click(screen.getByRole('button', { name: /cancel/i }))

      expect(onClose).toHaveBeenCalledTimes(1)
      expect(bodies).toHaveLength(0)
    })

    it('closes on Escape, inheriting Modal behaviour', async () => {
      const user = userEvent.setup()
      const { onClose } = renderModal()

      await user.keyboard('{Escape}')

      expect(onClose).toHaveBeenCalledTimes(1)
    })
  })
})
