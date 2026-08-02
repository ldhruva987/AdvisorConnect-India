import { describe, expect, it, vi } from 'vitest'
import userEvent from '@testing-library/user-event'
import { useLocation } from 'react-router-dom'
import { MOCK_ADVISOR_DTOS } from '@/test/mocks/handlers/advisors'
import { mapAdvisorDto } from '@/lib/mapAdvisor'
import { render, screen } from '@/test/test-utils'
import { AdvisorCard } from './AdvisorCard'

const advisor = mapAdvisorDto(MOCK_ADVISOR_DTOS[0])
const offlineAdvisor = mapAdvisorDto(MOCK_ADVISOR_DTOS[1])
const unverifiedAdvisor = mapAdvisorDto(MOCK_ADVISOR_DTOS[2])

/** Exposes the current pathname so navigation targets are assertable. */
function LocationProbe() {
  const { pathname } = useLocation()
  return <span data-testid="pathname">{pathname}</span>
}

function renderCard(props: Partial<React.ComponentProps<typeof AdvisorCard>> = {}) {
  return render(
    <>
      <LocationProbe />
      <AdvisorCard advisor={advisor} {...props} />
    </>,
    { route: '/explore' },
  )
}

describe('AdvisorCard', () => {
  describe('navigation', () => {
    // The bug this covers: "Free Chat" used to navigate to a bare `/chat`,
    // which opens the inbox with no conversation selected — the advisor the
    // user just clicked was silently dropped.
    it('sends "Free Chat" to /chat/:id, carrying the advisor id', async () => {
      const user = userEvent.setup()
      renderCard()

      await user.click(screen.getByRole('button', { name: 'Free Chat' }))

      expect(screen.getByTestId('pathname')).toHaveTextContent(`/chat/${advisor.id}`)
      expect(advisor.id).toBe('advisor-1')
    })

    it('never navigates to a bare /chat', async () => {
      const user = userEvent.setup()
      renderCard()

      await user.click(screen.getByRole('button', { name: 'Free Chat' }))

      expect(screen.getByTestId('pathname')).not.toHaveTextContent(/^\/chat$/)
    })

    it('sends "Book Video" to /book/:id', async () => {
      const user = userEvent.setup()
      renderCard()

      await user.click(screen.getByRole('button', { name: /book video/i }))

      expect(screen.getByTestId('pathname')).toHaveTextContent(`/book/${advisor.id}`)
    })

    it('sends a click on the card body to the advisor profile, keyed by username', async () => {
      const user = userEvent.setup()
      renderCard()

      await user.click(screen.getByText(advisor.title))

      expect(screen.getByTestId('pathname')).toHaveTextContent(`/advisor/${advisor.username}`)
    })

    it('does not also open the profile when a CTA is clicked', async () => {
      const user = userEvent.setup()
      renderCard()

      await user.click(screen.getByRole('button', { name: 'Free Chat' }))

      // The CTA stops propagation, so the card's own navigate never runs.
      expect(screen.getByTestId('pathname')).not.toHaveTextContent('/advisor/')
    })
  })

  describe('optional callbacks', () => {
    it('invokes onChatClick alongside navigating', async () => {
      const onChatClick = vi.fn()
      const user = userEvent.setup()
      renderCard({ onChatClick })

      await user.click(screen.getByRole('button', { name: 'Free Chat' }))

      expect(onChatClick).toHaveBeenCalledTimes(1)
      expect(screen.getByTestId('pathname')).toHaveTextContent(`/chat/${advisor.id}`)
    })

    it('invokes onBookClick alongside navigating', async () => {
      const onBookClick = vi.fn()
      const user = userEvent.setup()
      renderCard({ onBookClick })

      await user.click(screen.getByRole('button', { name: /book video/i }))

      expect(onBookClick).toHaveBeenCalledTimes(1)
    })
  })

  describe('content', () => {
    it('renders the advisor the props describe, not placeholder copy', () => {
      renderCard()

      expect(screen.getByText('maya_chen')).toBeInTheDocument()
      expect(screen.getByText('Career Transition Coach')).toBeInTheDocument()
      expect(screen.getByText(/Fifteen years helping people/)).toBeInTheDocument()
      expect(screen.getByText('4.8')).toBeInTheDocument()
      expect(screen.getByText('(132 reviews)')).toBeInTheDocument()
    })

    it('shows the verified mark and online pill only when the data says so', () => {
      const { rerender } = renderCard()

      expect(screen.getByLabelText('Verified advisor')).toBeInTheDocument()
      expect(screen.getByText('Online')).toBeInTheDocument()

      rerender(
        <>
          <LocationProbe />
          <AdvisorCard advisor={offlineAdvisor} />
        </>,
      )

      expect(screen.queryByText('Online')).not.toBeInTheDocument()
    })

    it('omits the verified mark for an unverified advisor', () => {
      render(<AdvisorCard advisor={unverifiedAdvisor} />, { route: '/explore' })

      expect(screen.queryByLabelText('Verified advisor')).not.toBeInTheDocument()
      expect(screen.getByText('rio_alvarez')).toBeInTheDocument()
    })

    it('caps the tag row at three tags', () => {
      const manyTags = { ...advisor, tags: ['a', 'b', 'c', 'd', 'e'] }
      render(<AdvisorCard advisor={manyTags} />, { route: '/explore' })

      expect(screen.getByText('a')).toBeInTheDocument()
      expect(screen.getByText('c')).toBeInTheDocument()
      expect(screen.queryByText('d')).not.toBeInTheDocument()
    })
  })
})
