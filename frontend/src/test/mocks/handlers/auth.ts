import { http, HttpResponse } from 'msw'
import type { TokenResponse } from '@/types/api'

/**
 * `POST /auth/login` and `POST /auth/register` — both REAL on the backend today.
 *
 * Register takes only `{ email, password }`: backend Phase 2 locks registration
 * to `role=USER`, so there is deliberately no role field to send. Both
 * endpoints return the same `TokenResponse` (register auto-logs-in).
 */

export const MOCK_LOGIN_RESPONSE: TokenResponse = {
  accessToken: 'mock-access-token',
  refreshToken: 'mock-refresh-token',
  userId: 'user-1',
  role: 'user',
  expiresIn: 900,
}

export const MOCK_REGISTER_RESPONSE: TokenResponse = {
  accessToken: 'new-access-token',
  refreshToken: 'new-refresh-token',
  userId: 'user-2',
  role: 'user',
  expiresIn: 900,
}

/** Credentials the login handler rejects, so tests can drive the failure path. */
export const REJECTED_PASSWORD = 'wrong-password'
/** Email the register handler treats as already taken. */
export const TAKEN_EMAIL = 'taken@example.com'

interface Credentials {
  email: string
  password: string
}

export const authHandlers = [
  http.post('*/api/auth/login', async ({ request }) => {
    const { password } = (await request.json()) as Credentials
    if (password === REJECTED_PASSWORD) {
      return HttpResponse.json({ message: 'Invalid email or password' }, { status: 401 })
    }
    return HttpResponse.json(MOCK_LOGIN_RESPONSE)
  }),

  http.post('*/api/auth/register', async ({ request }) => {
    const { email } = (await request.json()) as Credentials
    if (email === TAKEN_EMAIL) {
      return HttpResponse.json({ message: 'Email already registered' }, { status: 409 })
    }
    return HttpResponse.json(MOCK_REGISTER_RESPONSE, { status: 201 })
  }),

  // Not called by any hook — this is `apiClient`'s 401 refresh-retry
  // interceptor. Mocked so that a test which provokes a 401 on a *protected*
  // endpoint exercises the real retry path instead of failing on an
  // unhandled request.
  http.post('*/api/auth/refresh', () =>
    HttpResponse.json({
      accessToken: 'refreshed-access-token',
      refreshToken: 'refreshed-refresh-token',
    }),
  ),
]
