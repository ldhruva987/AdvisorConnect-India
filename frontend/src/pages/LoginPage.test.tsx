import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import userEvent from '@testing-library/user-event'
import { Route, Routes, useLocation } from 'react-router-dom'
import { server } from '@/test/mocks/server'
import { MOCK_LOGIN_RESPONSE, REJECTED_PASSWORD } from '@/test/mocks/handlers/auth'
import { useAuthStore } from '@/stores/authStore'
import { render, screen, waitFor } from '@/test/test-utils'
import { LoginPage } from './LoginPage'

/** Exposes the current pathname so navigation is assertable. */
function LocationProbe() {
  const location = useLocation()
  return <span data-testid="pathname">{location.pathname}</span>
}

function renderLoginPage() {
  return render(
    <>
      <LocationProbe />
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/explore" element={<p>explore page</p>} />
        <Route path="/dashboard" element={<p>advisor dashboard</p>} />
        <Route path="/admin" element={<p>admin page</p>} />
      </Routes>
    </>,
    { route: '/login' },
  )
}

/** Fills both fields and submits. Both are `required`, so both must be set. */
async function signIn(email: string, password: string) {
  const user = userEvent.setup()
  await user.type(screen.getByLabelText(/email address/i), email)
  await user.type(screen.getByLabelText(/password/i), password)
  await user.click(screen.getByRole('button', { name: /sign in/i }))
}

describe('LoginPage', () => {
  describe('successful sign-in', () => {
    it('navigates to /explore for a plain user', async () => {
      renderLoginPage()

      await signIn('someone@example.com', 'correct-horse')

      await waitFor(() => expect(screen.getByText('explore page')).toBeInTheDocument())
      expect(screen.getByTestId('pathname')).toHaveTextContent('/explore')
    })

    it('stores the credentials returned by the server, not hardcoded ones', async () => {
      // Deliberately distinct from MOCK_LOGIN_RESPONSE *and* from the mock
      // values the page used to hardcode, so this fails loudly if anything
      // ever goes back to inventing a session instead of using the response.
      server.use(
        http.post('*/api/auth/login', () =>
          HttpResponse.json({
            accessToken: 'server-issued-access',
            refreshToken: 'server-issued-refresh',
            userId: 'server-issued-user-id',
            role: 'user',
            expiresIn: 900,
          }),
        ),
      )

      renderLoginPage()
      await signIn('someone@example.com', 'correct-horse')

      await waitFor(() => expect(useAuthStore.getState().isAuthenticated).toBe(true))
      expect(useAuthStore.getState()).toMatchObject({
        userId: 'server-issued-user-id',
        role: 'user',
        accessToken: 'server-issued-access',
        refreshToken: 'server-issued-refresh',
      })
    })

    it('sends exactly what was typed to /auth/login', async () => {
      let body: unknown
      server.use(
        http.post('*/api/auth/login', async ({ request }) => {
          body = await request.json()
          return HttpResponse.json(MOCK_LOGIN_RESPONSE)
        }),
      )

      renderLoginPage()
      await signIn('typed@example.com', 'typed-password')

      await waitFor(() => expect(body).toEqual({
        email: 'typed@example.com',
        password: 'typed-password',
      }))
    })

    it('sends an admin to /admin — the demo button is gone, real login must route them', async () => {
      server.use(
        http.post('*/api/auth/login', () =>
          HttpResponse.json({ ...MOCK_LOGIN_RESPONSE, userId: 'admin-1', role: 'admin' }),
        ),
      )

      renderLoginPage()
      await signIn('admin@example.com', 'correct-horse')

      await waitFor(() => expect(screen.getByText('admin page')).toBeInTheDocument())
      expect(useAuthStore.getState().role).toBe('admin')
    })

    it('sends an advisor to /dashboard', async () => {
      server.use(
        http.post('*/api/auth/login', () =>
          HttpResponse.json({ ...MOCK_LOGIN_RESPONSE, userId: 'advisor-1', role: 'advisor' }),
        ),
      )

      renderLoginPage()
      await signIn('advisor@example.com', 'correct-horse')

      await waitFor(() => expect(screen.getByText('advisor dashboard')).toBeInTheDocument())
    })
  })

  describe('failed sign-in', () => {
    it('renders the backend message in an ErrorBanner and does not navigate', async () => {
      renderLoginPage()

      await signIn('someone@example.com', REJECTED_PASSWORD)

      const banner = await screen.findByRole('alert')
      expect(banner).toHaveTextContent('Invalid email or password')
      expect(screen.getByTestId('pathname')).toHaveTextContent('/login')
      expect(screen.queryByText('explore page')).not.toBeInTheDocument()
    })

    it('leaves the auth store untouched', async () => {
      renderLoginPage()

      await signIn('someone@example.com', REJECTED_PASSWORD)

      await screen.findByRole('alert')
      expect(useAuthStore.getState().isAuthenticated).toBe(false)
      expect(useAuthStore.getState().accessToken).toBeNull()
    })

    it('does not leak the raw Axios message on a 500', async () => {
      server.use(http.post('*/api/auth/login', () => new HttpResponse(null, { status: 500 })))

      renderLoginPage()
      await signIn('someone@example.com', 'correct-horse')

      const banner = await screen.findByRole('alert')
      expect(banner).toHaveTextContent('Something went wrong. Please try again.')
      expect(banner).not.toHaveTextContent(/status code/i)
    })

    it('clears the previous error when the retry succeeds', async () => {
      renderLoginPage()

      await signIn('someone@example.com', REJECTED_PASSWORD)
      await screen.findByRole('alert')

      // Correct the password and resubmit.
      const user = userEvent.setup()
      await user.clear(screen.getByLabelText(/password/i))
      await user.type(screen.getByLabelText(/password/i), 'correct-horse')
      await user.click(screen.getByRole('button', { name: /sign in/i }))

      await waitFor(() => expect(screen.getByText('explore page')).toBeInTheDocument())
    })
  })

  describe('the removed "Admin login (demo)" escape hatch', () => {
    // Regression guard. This button used to log anyone in as an admin with a
    // fabricated token and no server round trip.
    it('is not rendered anywhere on the page', () => {
      renderLoginPage()

      expect(screen.queryByText(/admin login/i)).not.toBeInTheDocument()
      expect(screen.queryByText(/demo/i)).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: /admin/i })).not.toBeInTheDocument()
    })

    it('leaves exactly one button — the real submit', () => {
      renderLoginPage()

      const buttons = screen.getAllByRole('button')
      expect(buttons).toHaveLength(1)
      expect(buttons[0]).toHaveTextContent(/sign in/i)
    })
  })
})
