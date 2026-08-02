import { describe, expect, it } from 'vitest'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { RouterProvider, createMemoryRouter } from 'react-router-dom'
import { QueryClientProvider } from '@tanstack/react-query'
// Raw RTL render on purpose: `render` from test-utils injects its own
// MemoryRouter, and a router nested inside a router would defeat the point of
// mounting the real route table.
import { render as rtlRender } from '@testing-library/react'
import { server } from '@/test/mocks/server'
import { MOCK_LOGIN_RESPONSE } from '@/test/mocks/handlers/auth'
import { createTestQueryClient, loginAs, screen, waitFor } from '@/test/test-utils'
import { routes } from './router'

/**
 * Mounts the REAL route table from `router.tsx` in a memory router.
 *
 * `ProtectedRoute.test.tsx` proves the guard component behaves; this proves the
 * guard is actually *attached* to `/dashboard` and `/admin`. Without this,
 * deleting the wrapper from `router.tsx` would leave every test green.
 */
function renderApp(initialEntry: string) {
  const router = createMemoryRouter(routes, { initialEntries: [initialEntry] })
  const queryClient = createTestQueryClient()

  return rtlRender(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
}

/** Text unique to each destination, used to assert where we actually landed. */
// The advisor dashboard no longer greets a hardcoded "Rohan" — nothing serves
// the signed-in advisor's own name yet — so the sidebar heading is the stable
// landmark for "we landed on the advisor dashboard".
const ADVISOR_DASHBOARD = /your dashboard/i
const ADMIN_DASHBOARD = /admin overview/i
const LOGIN_PAGE = /sign in to your advisorconnect account/i
const EXPLORE_PAGE = /find an advisor/i

describe('router guards', () => {
  describe('/dashboard', () => {
    it('redirects an unauthenticated visitor to /login', () => {
      renderApp('/dashboard')

      expect(screen.getByText(LOGIN_PAGE)).toBeInTheDocument()
      expect(screen.queryByText(ADVISOR_DASHBOARD)).not.toBeInTheDocument()
    })

    it('redirects a signed-in plain user to /explore', () => {
      loginAs({ role: 'user' })

      renderApp('/dashboard')

      expect(screen.getByText(EXPLORE_PAGE)).toBeInTheDocument()
      expect(screen.queryByText(ADVISOR_DASHBOARD)).not.toBeInTheDocument()
    })

    it('redirects an admin to /admin', () => {
      loginAs({ role: 'admin' })

      renderApp('/dashboard')

      expect(screen.getByText(ADMIN_DASHBOARD)).toBeInTheDocument()
    })

    it('renders for an advisor', () => {
      loginAs({ role: 'advisor' })

      renderApp('/dashboard')

      expect(screen.getByText(ADVISOR_DASHBOARD)).toBeInTheDocument()
    })
  })

  describe('/admin', () => {
    it('redirects an unauthenticated visitor to /login', () => {
      renderApp('/admin')

      expect(screen.getByText(LOGIN_PAGE)).toBeInTheDocument()
      expect(screen.queryByText(ADMIN_DASHBOARD)).not.toBeInTheDocument()
    })

    it('redirects a signed-in plain user to /explore', () => {
      loginAs({ role: 'user' })

      renderApp('/admin')

      expect(screen.getByText(EXPLORE_PAGE)).toBeInTheDocument()
      expect(screen.queryByText(ADMIN_DASHBOARD)).not.toBeInTheDocument()
    })

    it('redirects an advisor to /dashboard', () => {
      loginAs({ role: 'advisor' })

      renderApp('/admin')

      expect(screen.getByText(ADVISOR_DASHBOARD)).toBeInTheDocument()
      expect(screen.queryByText(ADMIN_DASHBOARD)).not.toBeInTheDocument()
    })

    it('renders for an admin', () => {
      loginAs({ role: 'admin' })

      renderApp('/admin')

      expect(screen.getByText(ADMIN_DASHBOARD)).toBeInTheDocument()
    })
  })

  /**
   * `/bookings` is the one guarded route with no `allowedRoles`: both sides of
   * a booking need to see their own sessions, so the bar is "signed in" and
   * nothing more. These cases exist so a stray `allowedRoles={['user']}` added
   * later can't quietly lock advisors out of their own session list.
   */
  describe('/bookings', () => {
    const MY_BOOKINGS_PAGE = /my bookings/i

    it('redirects an unauthenticated visitor to /login', () => {
      renderApp('/bookings')

      expect(screen.getByText(LOGIN_PAGE)).toBeInTheDocument()
      expect(screen.queryByRole('heading', { name: MY_BOOKINGS_PAGE })).not.toBeInTheDocument()
    })

    it.each(['user', 'advisor', 'admin'] as const)('renders for a signed-in %s', (role) => {
      loginAs({ role })

      renderApp('/bookings')

      expect(screen.getByRole('heading', { name: MY_BOOKINGS_PAGE })).toBeInTheDocument()
    })
  })

  describe('unguarded routes stay open', () => {
    it('lets an unauthenticated visitor reach /explore', () => {
      renderApp('/explore')

      expect(screen.getByText(EXPLORE_PAGE)).toBeInTheDocument()
    })
  })

  describe('bounce-back after sign-in', () => {
    it('returns an advisor to the /dashboard they were denied', async () => {
      server.use(
        http.post('*/api/auth/login', () =>
          HttpResponse.json({ ...MOCK_LOGIN_RESPONSE, userId: 'advisor-1', role: 'advisor' }),
        ),
      )

      // Denied, and bounced to /login with `from` recorded in router state.
      renderApp('/dashboard')
      expect(screen.getByText(LOGIN_PAGE)).toBeInTheDocument()

      const user = userEvent.setup()
      await user.type(screen.getByLabelText(/email address/i), 'advisor@example.com')
      await user.type(screen.getByLabelText(/password/i), 'correct-horse')
      await user.click(screen.getByRole('button', { name: /sign in/i }))

      await waitFor(() => expect(screen.getByText(ADVISOR_DASHBOARD)).toBeInTheDocument())
    })
  })
})
