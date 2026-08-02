import { describe, expect, it, vi } from 'vitest'
import userEvent from '@testing-library/user-event'
import { SearchX } from 'lucide-react'
import { render, screen } from '@/test/test-utils'
import { Button } from './Button'
import { EmptyState } from './EmptyState'

describe('EmptyState', () => {
  it('renders the title', () => {
    render(<EmptyState title="No advisors found" />)

    expect(screen.getByText('No advisors found')).toBeInTheDocument()
  })

  it('renders the optional description', () => {
    render(
      <EmptyState title="No advisors found" description="Try adjusting your search or filters" />,
    )

    expect(screen.getByText('Try adjusting your search or filters')).toBeInTheDocument()
  })

  it('omits the description when not supplied', () => {
    const { container } = render(<EmptyState title="Nothing here" />)

    expect(container.querySelectorAll('p')).toHaveLength(1)
  })

  it('renders an icon decoratively', () => {
    const { container } = render(
      <EmptyState icon={<SearchX data-testid="icon" />} title="No advisors found" />,
    )

    expect(screen.getByTestId('icon')).toBeInTheDocument()
    expect(container.querySelector('[aria-hidden="true"]')).not.toBeNull()
  })

  it('renders an interactive action', async () => {
    const user = userEvent.setup()
    const onClear = vi.fn()
    render(
      <EmptyState
        title="No advisors found"
        action={<Button onClick={onClear}>Clear filters</Button>}
      />,
    )

    await user.click(screen.getByRole('button', { name: 'Clear filters' }))

    expect(onClear).toHaveBeenCalledTimes(1)
  })

  it('renders no action slot when none is given', () => {
    render(<EmptyState title="No advisors found" />)

    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })
})
