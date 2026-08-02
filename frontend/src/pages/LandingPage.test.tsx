import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { server } from '@/test/mocks/server'
import { MOCK_ADVISOR_DTOS } from '@/test/mocks/handlers/advisors'
import { ALL_SECTORS, SECTOR_LABELS, isAdvisorSectorEnum } from '@/lib/sectors'
import { render, screen, waitFor, within } from '@/test/test-utils'
import type { AdvisorPublicDto } from '@/types/api'
import { LandingPage } from './LandingPage'

function pageOf(content: AdvisorPublicDto[]) {
  return {
    content,
    totalElements: content.length,
    totalPages: content.length === 0 ? 0 : 1,
    number: 0,
    size: 20,
  }
}

/** The "Featured Advisors" section, scoped so hero/pricing copy can't match. */
function featuredSection(): HTMLElement {
  return screen.getByRole('heading', { name: 'Featured Advisors' }).closest('section')!
}

/**
 * The hero category strip. Scoped because the footer's "Sectors" column links to
 * the same four destinations by the same names — an unscoped `getByRole('link',
 * { name: 'Career' })` matches both.
 */
function pills() {
  return within(screen.getByRole('region', { name: 'Browse by sector' }))
}

describe('LandingPage', () => {
  describe('featured advisors', () => {
    it('renders advisors from the API rather than a hardcoded list', async () => {
      render(<LandingPage />)

      expect(await screen.findByText('maya_chen')).toBeInTheDocument()
      expect(screen.getByText('sam_okafor')).toBeInTheDocument()
      expect(screen.getByText('rio_alvarez')).toBeInTheDocument()
      expect(screen.getByText('Career Transition Coach')).toBeInTheDocument()
      expect(screen.getByText('Financial Planner')).toBeInTheDocument()
    })

    it('does not render the fabricated advisors the page used to ship with', async () => {
      render(<LandingPage />)
      await screen.findByText('maya_chen')

      expect(screen.queryByText('@MindfulRohan')).not.toBeInTheDocument()
      expect(screen.queryByText('@SarahCareerPro')).not.toBeInTheDocument()
      expect(screen.queryByText('Licensed Clinical Psychologist')).not.toBeInTheDocument()
    })

    it('caps the strip at four advisors even when the API returns more', async () => {
      const many = Array.from({ length: 9 }, (_, i) => ({
        ...MOCK_ADVISOR_DTOS[0],
        id: `advisor-${i}`,
        username: `advisor_number_${i}`,
      }))
      server.use(http.get('*/api/advisors', () => HttpResponse.json(pageOf(many))))
      render(<LandingPage />)

      await screen.findByText('advisor_number_0')
      expect(screen.getByText('advisor_number_3')).toBeInTheDocument()
      expect(screen.queryByText('advisor_number_4')).not.toBeInTheDocument()
    })

    it('shows skeleton cards while the request is in flight', () => {
      render(<LandingPage />)

      expect(screen.getAllByRole('status', { name: /loading advisor/i })).toHaveLength(4)
      expect(screen.queryByText('maya_chen')).not.toBeInTheDocument()
    })

    it('shows an EmptyState when the API returns no advisors', async () => {
      server.use(http.get('*/api/advisors', () => HttpResponse.json(pageOf([]))))
      render(<LandingPage />)

      expect(await screen.findByText('No advisors available yet')).toBeInTheDocument()
      expect(screen.getByText(/new advisors are verified regularly/i)).toBeInTheDocument()
    })

    it('shows an ErrorBanner when the request fails, without breaking the page', async () => {
      server.use(http.get('*/api/advisors', () => new HttpResponse(null, { status: 500 })))
      render(<LandingPage />)

      expect(await screen.findByRole('alert')).toHaveTextContent(
        'Something went wrong. Please try again.',
      )
      // The rest of the marketing page still renders.
      expect(screen.getByRole('heading', { name: 'How It Works' })).toBeInTheDocument()
      expect(screen.getByRole('heading', { name: 'Simple, Honest Pricing' })).toBeInTheDocument()
    })

    it('links "View all" to the explore page', async () => {
      render(<LandingPage />)
      await screen.findByText('maya_chen')

      expect(within(featuredSection()).getByRole('link', { name: /view all/i })).toHaveAttribute(
        'href',
        '/explore',
      )
    })
  })

  describe('category pills', () => {
    it('renders one pill per sector in the shared vocabulary', async () => {
      render(<LandingPage />)
      await screen.findByText('maya_chen')

      for (const value of ALL_SECTORS) {
        expect(pills().getByRole('link', { name: SECTOR_LABELS[value] })).toBeInTheDocument()
      }
    })

    // The bug this covers: pills used to link to `?sector=Mental+Health` (the
    // display label). ExplorePage parses that param with `isAdvisorSectorEnum`,
    // so every one of those links silently produced an unfiltered list.
    it('links each pill to /explore with the backend enum value', async () => {
      render(<LandingPage />)
      await screen.findByText('maya_chen')

      expect(pills().getByRole('link', { name: 'Mental Health' })).toHaveAttribute(
        'href',
        '/explore?sector=MENTAL_HEALTH',
      )
      expect(pills().getByRole('link', { name: 'Health & Wellness' })).toHaveAttribute(
        'href',
        '/explore?sector=HEALTH_WELLNESS',
      )
      expect(pills().getByRole('link', { name: 'Career' })).toHaveAttribute(
        'href',
        '/explore?sector=CAREER',
      )
    })

    it('emits no ?sector= value that ExplorePage would reject', async () => {
      render(<LandingPage />)
      await screen.findByText('maya_chen')

      const sectorLinks = Array.from(document.querySelectorAll('a[href*="sector="]'))
      expect(sectorLinks.length).toBeGreaterThan(0)

      for (const link of sectorLinks) {
        const value = new URL(link.getAttribute('href')!, 'http://x').searchParams.get('sector')!
        // Fails loudly on `Mental+Health`, `Career`, or any other label form —
        // including the footer's sector column.
        expect(isAdvisorSectorEnum(value)).toBe(true)
      }
    })
  })

  describe('no fabricated statistics', () => {
    /**
     * Regression guard for invented social proof. The hero used to claim a live
     * count of advisors online and an aggregate rating "from 28,000+ sessions";
     * neither figure has any backend source, and DESIGN.md rules out
     * manufactured urgency. These must not creep back in.
     */
    it('states availability without inventing a live count', async () => {
      render(<LandingPage />)
      await screen.findByText('maya_chen')

      expect(screen.getByText('Verified advisors available now')).toBeInTheDocument()

      const text = document.body.textContent ?? ''
      expect(text).not.toMatch(/1,?200/)
      expect(text).not.toMatch(/online now/i)
      expect(text).not.toMatch(/advisors online/i)
      expect(text).not.toMatch(/\d[\d,]*\+?\s*(advisors|experts|professionals|members)\s*online/i)
    })

    it('makes no aggregate-rating or session-volume claim', async () => {
      render(<LandingPage />)
      await screen.findByText('maya_chen')

      const text = document.body.textContent ?? ''
      expect(text).not.toMatch(/28,?000/)
      expect(text).not.toMatch(/\d[\d,]*\+\s*sessions/i)
      expect(text).not.toMatch(/4\.9\s*★/)
      expect(text).not.toMatch(/rated\s*4\.\d/i)
    })

    it('shows no per-sector advisor counts on the pills', async () => {
      render(<LandingPage />)
      await screen.findByText('maya_chen')

      // There is no per-sector count endpoint; the pills carry name + icon only.
      for (const value of ALL_SECTORS) {
        const pill = pills().getByRole('link', { name: SECTOR_LABELS[value] })
        expect(pill.textContent ?? '').not.toMatch(/\d/)
      }
    })
  })

  describe('primary calls to action', () => {
    it('routes both hero CTAs to real destinations', async () => {
      render(<LandingPage />)
      await screen.findByText('maya_chen')

      expect(screen.getByRole('button', { name: 'Start Free Chat' }).closest('a')).toHaveAttribute(
        'href',
        '/explore',
      )
      expect(
        screen.getByRole('button', { name: 'Become an Advisor' }).closest('a'),
      ).toHaveAttribute('href', '/onboarding')
    })

    it('routes the pricing and closing CTAs', async () => {
      render(<LandingPage />)
      await screen.findByText('maya_chen')

      expect(
        screen.getByRole('button', { name: 'Get Started Free' }).closest('a'),
      ).toHaveAttribute('href', '/register')
      expect(screen.getByRole('button', { name: 'Book a Session' }).closest('a')).toHaveAttribute(
        'href',
        '/explore',
      )
      expect(
        screen.getByRole('button', { name: 'Start Your Free Chat Now' }).closest('a'),
      ).toHaveAttribute('href', '/explore')
    })
  })

  describe('requests', () => {
    it('asks for the first page once', async () => {
      const urls: URL[] = []
      server.use(
        http.get('*/api/advisors', ({ request }) => {
          urls.push(new URL(request.url))
          return HttpResponse.json(pageOf(MOCK_ADVISOR_DTOS))
        }),
      )
      render(<LandingPage />)
      await screen.findByText('maya_chen')

      await waitFor(() => expect(urls).toHaveLength(1))
      expect(urls[0].searchParams.get('page')).toBe('0')
      expect(urls[0].searchParams.has('sector')).toBe(false)
    })
  })
})
