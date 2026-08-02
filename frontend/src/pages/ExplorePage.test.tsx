import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import userEvent from '@testing-library/user-event'
import { useLocation } from 'react-router-dom'
import { server } from '@/test/mocks/server'
import { MOCK_ADVISOR_DTOS } from '@/test/mocks/handlers/advisors'
import { render, screen, waitFor, within } from '@/test/test-utils'
import type { AdvisorPublicDto } from '@/types/api'
import { ExplorePage } from './ExplorePage'

/** Exposes the query string so URL writes are assertable. */
function SearchProbe() {
  const location = useLocation()
  return <span data-testid="search">{location.search}</span>
}

function renderExplore(route = '/explore') {
  return render(
    <>
      <SearchProbe />
      <ExplorePage />
    </>,
    { route },
  )
}

function pageOf(content: AdvisorPublicDto[], totalPages = 1) {
  return {
    content,
    totalElements: content.length,
    totalPages,
    number: 0,
    size: 20,
  }
}

/**
 * Usernames in DOM order. `AdvisorCard` renders the username as the entire text
 * of one span, so this reads the rendered ordering without a test-only hook.
 */
function usernameOrder(container: HTMLElement): string[] {
  return Array.from(container.querySelectorAll('span'))
    .map((el) => el.textContent ?? '')
    .filter((text) => /^(maya_chen|sam_okafor|rio_alvarez)$/.test(text))
}

/**
 * Replaces the search handler with one that records every request it sees, so
 * tests can assert on the params actually sent rather than only on the DOM.
 */
function captureAdvisorRequests() {
  const urls: URL[] = []
  server.use(
    http.get('*/api/advisors', ({ request }) => {
      const url = new URL(request.url)
      urls.push(url)
      const sector = url.searchParams.get('sector')
      const q = url.searchParams.get('q')?.toLowerCase()
      let content = MOCK_ADVISOR_DTOS
      if (sector) content = content.filter((a) => a.sectors.includes(sector as never))
      if (q) content = content.filter((a) => a.username.toLowerCase().includes(q))
      return HttpResponse.json(pageOf(content))
    }),
  )
  return urls
}

describe('ExplorePage', () => {
  describe('loading', () => {
    it('shows a grid of skeleton cards before the first page resolves', () => {
      renderExplore()

      expect(screen.getAllByRole('status', { name: /loading advisor/i })).toHaveLength(8)
      expect(screen.getByText(/loading advisors/i)).toBeInTheDocument()
    })

    it('swaps skeletons for real cards once the request settles', async () => {
      renderExplore()

      expect(screen.getAllByRole('status', { name: /loading advisor/i }).length).toBeGreaterThan(0)
      await screen.findByText('maya_chen')
      expect(screen.queryByRole('status', { name: /loading advisor/i })).not.toBeInTheDocument()
    })
  })

  describe('rendering real data', () => {
    it('renders the advisors the API returned', async () => {
      renderExplore()

      expect(await screen.findByText('maya_chen')).toBeInTheDocument()
      expect(screen.getByText('sam_okafor')).toBeInTheDocument()
      expect(screen.getByText('rio_alvarez')).toBeInTheDocument()
      expect(screen.getByText('Career Transition Coach')).toBeInTheDocument()
    })

    it('does not render the hardcoded advisors the page used to ship with', async () => {
      renderExplore()
      await screen.findByText('maya_chen')

      // Regression guard: eight fabricated advisors used to render regardless
      // of what the API said.
      expect(screen.queryByText('@MindfulRohan')).not.toBeInTheDocument()
      expect(screen.queryByText('@SarahCareerPro')).not.toBeInTheDocument()
      expect(screen.queryByText('Licensed Clinical Psychologist')).not.toBeInTheDocument()
    })

    it('shows the real total from the API, not a hardcoded count', async () => {
      renderExplore()

      expect(await screen.findByText('3 advisors')).toBeInTheDocument()
      expect(screen.queryByText(/248 advisors/)).not.toBeInTheDocument()
    })

    it('singularises the count when the API returns one advisor', async () => {
      server.use(http.get('*/api/advisors', () => HttpResponse.json(pageOf([MOCK_ADVISOR_DTOS[0]]))))
      renderExplore()

      expect(await screen.findByText('1 advisor')).toBeInTheDocument()
    })
  })

  describe('category pills', () => {
    it('offers every sector in the shared vocabulary', async () => {
      renderExplore()
      await screen.findByText('maya_chen')

      expect(screen.getByRole('button', { name: 'All' })).toBeInTheDocument()
      // Both of these were missing from the old hardcoded CATEGORIES array.
      expect(screen.getByRole('button', { name: 'Parenting' })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Health & Wellness' })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Life Coaching' })).toBeInTheDocument()
    })

    it('writes the sector to the URL and refetches with it', async () => {
      const urls = captureAdvisorRequests()
      const user = userEvent.setup()
      renderExplore()
      await screen.findByText('maya_chen')

      await user.click(screen.getByRole('button', { name: 'Career' }))

      await waitFor(() => {
        expect(screen.getByTestId('search')).toHaveTextContent('sector=CAREER')
      })
      // The new sector actually reached the server...
      await waitFor(() => {
        expect(urls.some((u) => u.searchParams.get('sector') === 'CAREER')).toBe(true)
      })
      // ...and the list narrowed to what came back for it.
      await waitFor(() => expect(screen.queryByText('sam_okafor')).not.toBeInTheDocument())
      expect(screen.getByText('maya_chen')).toBeInTheDocument()
    })

    it('marks the active sector as pressed', async () => {
      const user = userEvent.setup()
      renderExplore()
      await screen.findByText('maya_chen')

      await user.click(screen.getByRole('button', { name: 'Finance' }))

      await waitFor(() =>
        expect(screen.getByRole('button', { name: 'Finance' })).toHaveAttribute('aria-pressed', 'true'),
      )
      expect(screen.getByRole('button', { name: 'All' })).toHaveAttribute('aria-pressed', 'false')
    })

    it('clears the filter when "All" is chosen', async () => {
      const user = userEvent.setup()
      renderExplore('/explore?sector=CAREER')
      await screen.findByText('maya_chen')

      await user.click(screen.getByRole('button', { name: 'All' }))

      await waitFor(() => expect(screen.getByText('sam_okafor')).toBeInTheDocument())
      expect(screen.getByTestId('search')).not.toHaveTextContent('sector')
    })
  })

  describe('deep links', () => {
    // The bug this fixes: LandingPage already linked to `/explore?sector=...`
    // but ExplorePage never read the query string, so the filter was dropped.
    it('applies ?sector= from the incoming URL on first render', async () => {
      const urls = captureAdvisorRequests()
      renderExplore('/explore?sector=MENTAL_HEALTH')

      expect(await screen.findByText('sam_okafor')).toBeInTheDocument()
      expect(screen.queryByText('maya_chen')).not.toBeInTheDocument()
      expect(urls[0].searchParams.get('sector')).toBe('MENTAL_HEALTH')
      // "Mental Health" appears twice on a filtered page — as the pill and in
      // the count line — so each is asserted on its own rather than with a bare
      // text query that would match both.
      expect(screen.getByRole('button', { name: 'Mental Health' })).toHaveAttribute(
        'aria-pressed',
        'true',
      )
      expect(screen.getByText(/· Mental Health/)).toBeInTheDocument()
    })

    it('applies ?q= from the incoming URL', async () => {
      const urls = captureAdvisorRequests()
      renderExplore('/explore?q=rio')

      await waitFor(() => expect(urls[0].searchParams.get('q')).toBe('rio'))
      expect(await screen.findByText('rio_alvarez')).toBeInTheDocument()
      expect(screen.getByDisplayValue('rio')).toBeInTheDocument()
    })

    it('ignores a sector value outside the known vocabulary', async () => {
      const urls = captureAdvisorRequests()
      renderExplore('/explore?sector=ASTROLOGY')

      // Unfiltered rather than an empty list or a crash.
      expect(await screen.findByText('maya_chen')).toBeInTheDocument()
      expect(urls[0].searchParams.get('sector')).toBeNull()
    })
  })

  describe('search', () => {
    it('debounces typing into the q param and refetches', async () => {
      const urls = captureAdvisorRequests()
      const user = userEvent.setup()
      renderExplore()
      await screen.findByText('maya_chen')

      await user.type(screen.getByLabelText(/search advisors/i), 'maya')

      await waitFor(() => expect(screen.getByTestId('search')).toHaveTextContent('q=maya'))
      await waitFor(() => expect(urls.some((u) => u.searchParams.get('q') === 'maya')).toBe(true))
      // One request per settled search term, not one per keystroke.
      expect(urls.filter((u) => u.searchParams.has('q')).length).toBe(1)
    })
  })

  describe('sorting', () => {
    it('re-sorts the fetched page on the client without a new request', async () => {
      const urls = captureAdvisorRequests()
      const user = userEvent.setup()
      const { container } = renderExplore()
      await screen.findByText('maya_chen')

      const requestsBefore = urls.length
      // Default is rating desc: sam 4.9, maya 4.8, rio 4.6.
      expect(usernameOrder(container)).toEqual(['sam_okafor', 'maya_chen', 'rio_alvarez'])

      await user.selectOptions(screen.getByLabelText(/sort advisors/i), 'reviews')

      // Review counts: maya 132, sam 87, rio 41.
      await waitFor(() =>
        expect(usernameOrder(container)).toEqual(['maya_chen', 'sam_okafor', 'rio_alvarez']),
      )
      // Sorting is a client-side reorder — the endpoint has no sort param.
      expect(urls.length).toBe(requestsBefore)
      expect(urls.every((u) => !u.searchParams.has('sort'))).toBe(true)
    })

    it('sorts by fastest response time', async () => {
      const user = userEvent.setup()
      const { container } = renderExplore()
      await screen.findByText('maya_chen')

      await user.selectOptions(screen.getByLabelText(/sort advisors/i), 'response')

      // Response minutes: maya 12, sam 30, rio 45.
      await waitFor(() =>
        expect(usernameOrder(container)).toEqual(['maya_chen', 'sam_okafor', 'rio_alvarez']),
      )
    })

    it('puts online advisors first when sorting by online', async () => {
      const user = userEvent.setup()
      const { container } = renderExplore()
      await screen.findByText('maya_chen')

      await user.selectOptions(screen.getByLabelText(/sort advisors/i), 'online')

      // sam_okafor is the only offline advisor in the fixture.
      await waitFor(() => expect(usernameOrder(container).at(-1)).toBe('sam_okafor'))
    })
  })

  describe('empty state', () => {
    it('renders an EmptyState when the API returns no advisors', async () => {
      server.use(http.get('*/api/advisors', () => HttpResponse.json(pageOf([]))))
      renderExplore()

      expect(await screen.findByText('No advisors found')).toBeInTheDocument()
      expect(screen.getByText(/adjusting your search or filters/i)).toBeInTheDocument()
      expect(screen.getByText('0 advisors')).toBeInTheDocument()
    })

    it('offers a clear-filters action that resets the URL', async () => {
      server.use(http.get('*/api/advisors', () => HttpResponse.json(pageOf([]))))
      const user = userEvent.setup()
      renderExplore('/explore?sector=PARENTING&q=nobody')

      await user.click(await screen.findByRole('button', { name: /clear filters/i }))

      await waitFor(() => {
        const search = screen.getByTestId('search').textContent ?? ''
        expect(search).not.toMatch(/sector|q=/)
      })
    })

    it('omits the clear-filters action when nothing is filtered', async () => {
      server.use(http.get('*/api/advisors', () => HttpResponse.json(pageOf([]))))
      renderExplore()

      await screen.findByText('No advisors found')
      expect(screen.queryByRole('button', { name: /clear filters/i })).not.toBeInTheDocument()
    })
  })

  describe('error state', () => {
    it('renders an ErrorBanner instead of the grid', async () => {
      server.use(http.get('*/api/advisors', () => new HttpResponse(null, { status: 500 })))
      renderExplore()

      const banner = await screen.findByRole('alert')
      expect(banner).toHaveTextContent('Something went wrong. Please try again.')
      expect(screen.queryByText('maya_chen')).not.toBeInTheDocument()
    })

    it('recovers when the retry succeeds', async () => {
      let attempt = 0
      server.use(
        http.get('*/api/advisors', () => {
          attempt += 1
          if (attempt === 1) return new HttpResponse(null, { status: 500 })
          return HttpResponse.json(pageOf(MOCK_ADVISOR_DTOS))
        }),
      )
      const user = userEvent.setup()
      renderExplore()

      const banner = await screen.findByRole('alert')
      await user.click(within(banner).getByRole('button', { name: /try again/i }))

      expect(await screen.findByText('maya_chen')).toBeInTheDocument()
      expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    })
  })

  describe('pagination', () => {
    it('is hidden when there is only one page', async () => {
      renderExplore()
      await screen.findByText('maya_chen')

      expect(screen.queryByRole('navigation', { name: /pagination/i })).not.toBeInTheDocument()
    })

    it('advances the page param and refetches', async () => {
      const urls: URL[] = []
      server.use(
        http.get('*/api/advisors', ({ request }) => {
          urls.push(new URL(request.url))
          return HttpResponse.json(pageOf(MOCK_ADVISOR_DTOS, 3))
        }),
      )
      const user = userEvent.setup()
      renderExplore()
      await screen.findByText('maya_chen')

      expect(screen.getByText('Page 1 of 3')).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /previous/i })).toBeDisabled()

      await user.click(screen.getByRole('button', { name: /next/i }))

      await waitFor(() => expect(screen.getByTestId('search')).toHaveTextContent('page=1'))
      await waitFor(() => expect(urls.some((u) => u.searchParams.get('page') === '1')).toBe(true))
      expect(screen.getByText('Page 2 of 3')).toBeInTheDocument()
    })

    it('disables next on the final page', async () => {
      server.use(http.get('*/api/advisors', () => HttpResponse.json(pageOf(MOCK_ADVISOR_DTOS, 2))))
      renderExplore('/explore?page=1')
      await screen.findByText('maya_chen')

      expect(screen.getByRole('button', { name: /next/i })).toBeDisabled()
      expect(screen.getByRole('button', { name: /previous/i })).toBeEnabled()
    })
  })
})
