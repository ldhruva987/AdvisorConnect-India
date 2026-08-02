import { describe, expect, it, vi } from 'vitest'
import userEvent from '@testing-library/user-event'
import { render, screen } from '@/test/test-utils'
import { ErrorBanner } from './ErrorBanner'

describe('ErrorBanner', () => {
  it('renders the message', () => {
    render(<ErrorBanner message="Invalid email or password." />)

    expect(screen.getByText('Invalid email or password.')).toBeInTheDocument()
  })

  it('announces itself as an alert', () => {
    render(<ErrorBanner message="Something broke." />)

    expect(screen.getByRole('alert')).toHaveTextContent('Something broke.')
  })

  it('uses danger tokens, never oxblood (reserved for primary CTAs)', () => {
    render(<ErrorBanner message="Nope." />)

    const banner = screen.getByRole('alert')
    expect(banner).toHaveClass('bg-danger-100', 'text-danger-600')
    expect(banner.className).not.toMatch(/oxblood/)
  })

  it('renders no retry affordance when onRetry is omitted', () => {
    render(<ErrorBanner message="Nope." />)

    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })

  it('calls onRetry when the retry button is clicked', async () => {
    const user = userEvent.setup()
    const onRetry = vi.fn()
    render(<ErrorBanner message="Could not load advisors." onRetry={onRetry} />)

    await user.click(screen.getByRole('button', { name: /try again/i }))

    expect(onRetry).toHaveBeenCalledTimes(1)
  })

  it('does not submit the surrounding form when used inside one', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn((e: React.FormEvent) => e.preventDefault())
    const onRetry = vi.fn()
    render(
      <form onSubmit={onSubmit}>
        <ErrorBanner message="Retry me." onRetry={onRetry} />
      </form>,
    )

    await user.click(screen.getByRole('button', { name: /try again/i }))

    expect(onRetry).toHaveBeenCalledTimes(1)
    expect(onSubmit).not.toHaveBeenCalled()
  })
})
