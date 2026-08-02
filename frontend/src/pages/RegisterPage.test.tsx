import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import userEvent from '@testing-library/user-event'
import { Route, Routes, useLocation } from 'react-router-dom'
import { server } from '@/test/mocks/server'
import { MOCK_REGISTER_RESPONSE, TAKEN_EMAIL } from '@/test/mocks/handlers/auth'
import { useAuthStore } from '@/stores/authStore'
import { render, screen, waitFor } from '@/test/test-utils'
import { RegisterPage } from './RegisterPage'

/** Exposes the current pathname so navigation is assertable. */
function LocationProbe() {
  const location = useLocation()
  return <span data-testid="pathname">{location.pathname}</span>
}

function renderRegisterPage() {
  return render(
    <>
      <LocationProbe />
      <Routes>
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/explore" element={<p>explore page</p>} />
        <Route path="/onboarding" element={<p>onboarding page</p>} />
      </Routes>
    </>,
    { route: '/register' },
  )
}

/**
 * Counts calls to `POST /auth/register` and records the last body, so tests can
 * prove the advisor path really registers rather than skipping straight to
 * onboarding as the old mock did.
 */
function spyOnRegister(response?: Parameters<typeof HttpResponse.json>[0], status = 201) {
  const calls: unknown[] = []
  server.use(
    http.post('*/api/auth/register', async ({ request }) => {
      calls.push(await request.json())
      return HttpResponse.json(response ?? MOCK_REGISTER_RESPONSE, { status })
    }),
  )
  return calls
}

async function fillAndSubmit(
  email: string,
  password: string,
  submitName: RegExp = /create free account/i,
) {
  const user = userEvent.setup()
  await user.type(screen.getByLabelText(/email address/i), email)
  await user.type(screen.getByLabelText(/password/i), password)
  await user.click(screen.getByRole('button', { name: submitName }))
}

/** Switches the form into advisor mode. */
async function chooseAdvisor() {
  await userEvent.setup().click(screen.getByRole('button', { name: /i'm an advisor/i }))
}

describe('RegisterPage', () => {
  describe('seeker path', () => {
    it('registers and navigates to /explore', async () => {
      renderRegisterPage()

      await fillAndSubmit('new@example.com', 'long-enough-password')

      await waitFor(() => expect(screen.getByText('explore page')).toBeInTheDocument())
      expect(screen.getByTestId('pathname')).toHaveTextContent('/explore')
    })

    it('logs the user in with the response, since register auto-logs-in', async () => {
      renderRegisterPage()

      await fillAndSubmit('new@example.com', 'long-enough-password')

      await waitFor(() => expect(useAuthStore.getState().isAuthenticated).toBe(true))
      expect(useAuthStore.getState()).toMatchObject({
        userId: MOCK_REGISTER_RESPONSE.userId,
        role: MOCK_REGISTER_RESPONSE.role,
        accessToken: MOCK_REGISTER_RESPONSE.accessToken,
        refreshToken: MOCK_REGISTER_RESPONSE.refreshToken,
      })
    })

    it('sends only email and password — never a role', async () => {
      const calls = spyOnRegister()

      renderRegisterPage()
      await fillAndSubmit('new@example.com', 'long-enough-password')

      await waitFor(() => expect(calls).toHaveLength(1))
      // A `role` field here would be the self-register-as-admin hole.
      expect(calls[0]).toEqual({ email: 'new@example.com', password: 'long-enough-password' })
    })

    it('rejects a password under 8 characters without calling the server', async () => {
      const calls = spyOnRegister()

      renderRegisterPage()
      await fillAndSubmit('new@example.com', 'short')

      expect(await screen.findByRole('alert')).toHaveTextContent(
        'Password must be at least 8 characters.',
      )
      expect(calls).toHaveLength(0)
      expect(screen.getByTestId('pathname')).toHaveTextContent('/register')
    })

    it('shows the backend message and does not navigate when the email is taken', async () => {
      renderRegisterPage()

      await fillAndSubmit(TAKEN_EMAIL, 'long-enough-password')

      expect(await screen.findByRole('alert')).toHaveTextContent('Email already registered')
      expect(screen.getByTestId('pathname')).toHaveTextContent('/register')
      expect(useAuthStore.getState().isAuthenticated).toBe(false)
    })
  })

  describe('advisor path', () => {
    it('creates a real account before onboarding', async () => {
      // The old mock navigated to /onboarding with zero account creation, which
      // left the applicant unauthenticated at an endpoint (`/advisors/apply`)
      // that requires auth. The mutation firing is the whole point of this test.
      const calls = spyOnRegister()

      renderRegisterPage()
      await chooseAdvisor()
      await fillAndSubmit('advisor@example.com', 'long-enough-password', /start advisor application/i)

      await waitFor(() => expect(calls).toHaveLength(1))
      expect(calls[0]).toEqual({
        email: 'advisor@example.com',
        password: 'long-enough-password',
      })
    })

    it('navigates to /onboarding only after the account exists', async () => {
      renderRegisterPage()
      await chooseAdvisor()

      // Not there before submitting.
      expect(screen.queryByText('onboarding page')).not.toBeInTheDocument()

      await fillAndSubmit('advisor@example.com', 'long-enough-password', /start advisor application/i)

      await waitFor(() => expect(screen.getByText('onboarding page')).toBeInTheDocument())
      expect(useAuthStore.getState().isAuthenticated).toBe(true)
    })

    it('does NOT navigate to /onboarding when registration fails', async () => {
      renderRegisterPage()
      await chooseAdvisor()
      await fillAndSubmit(TAKEN_EMAIL, 'long-enough-password', /start advisor application/i)

      expect(await screen.findByRole('alert')).toHaveTextContent('Email already registered')
      expect(screen.queryByText('onboarding page')).not.toBeInTheDocument()
      expect(screen.getByTestId('pathname')).toHaveTextContent('/register')
      expect(useAuthStore.getState().isAuthenticated).toBe(false)
    })

    it('asks for credentials rather than offering a bare "continue" link', async () => {
      renderRegisterPage()
      await chooseAdvisor()

      expect(screen.getByLabelText(/email address/i)).toBeInTheDocument()
      expect(screen.getByLabelText(/password/i)).toBeInTheDocument()
      expect(
        screen.getByRole('button', { name: /start advisor application/i }),
      ).toHaveAttribute('type', 'submit')
    })
  })
})
