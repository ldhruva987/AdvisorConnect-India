import { describe, expect, it } from 'vitest'
import { render, screen } from '@/test/test-utils'
import { Skeleton, SkeletonCard, SkeletonRow } from './Skeleton'

describe('Skeleton', () => {
  it('renders a muted, pulsing placeholder', () => {
    render(<Skeleton data-testid="skeleton" />)

    const el = screen.getByTestId('skeleton')
    expect(el).toBeInTheDocument()
    expect(el).toHaveClass('bg-ink-100')
  })

  it('gates the pulse behind motion-safe so reduced-motion users opt out', () => {
    render(<Skeleton data-testid="skeleton" />)

    const el = screen.getByTestId('skeleton')
    expect(el).toHaveClass('motion-safe:animate-pulse')
    // A bare `animate-pulse` would animate regardless of the user's preference.
    expect(el.className.split(/\s+/)).not.toContain('animate-pulse')
  })

  it('stays flat at rest per the DESIGN.md flat-by-default rule', () => {
    render(<Skeleton data-testid="skeleton" />)

    expect(screen.getByTestId('skeleton').className).not.toMatch(/(^|\s)shadow/)
  })

  it('is hidden from assistive tech so it is not read as content', () => {
    render(<Skeleton data-testid="skeleton" />)

    expect(screen.getByTestId('skeleton')).toHaveAttribute('aria-hidden', 'true')
  })

  it('merges caller classes over the defaults', () => {
    render(<Skeleton className="h-8 w-40" data-testid="skeleton" />)

    const el = screen.getByTestId('skeleton')
    expect(el).toHaveClass('h-8', 'w-40', 'bg-ink-100')
  })
})

describe('SkeletonCard', () => {
  it('announces itself as a loading region', () => {
    render(<SkeletonCard />)

    expect(screen.getByRole('status', { name: 'Loading advisor' })).toBeInTheDocument()
  })

  it('mirrors the AdvisorCard shell: bordered surface with an avatar circle', () => {
    render(<SkeletonCard data-testid="card" />)

    const card = screen.getByTestId('card')
    expect(card).toHaveClass('rounded-xl', 'border', 'border-ink-200')
    expect(card.querySelector('.rounded-full')).not.toBeNull()
  })

  it('contains several placeholder bars', () => {
    const { container } = render(<SkeletonCard />)

    expect(container.querySelectorAll('.motion-safe\\:animate-pulse').length).toBeGreaterThan(5)
  })
})

describe('SkeletonRow', () => {
  it('renders a table row with the requested number of cells', () => {
    render(
      <table>
        <tbody>
          <SkeletonRow columns={4} />
        </tbody>
      </table>,
    )

    expect(screen.getAllByRole('cell')).toHaveLength(4)
  })

  it('defaults to five columns', () => {
    render(
      <table>
        <tbody>
          <SkeletonRow />
        </tbody>
      </table>,
    )

    expect(screen.getAllByRole('cell')).toHaveLength(5)
  })
})
