import { describe, expect, it } from 'vitest'
import { Route, Routes, useLocation } from 'react-router-dom'
import { render, screen, seedAuthStore } from '@/test/test-utils'
import { ProtectedRoute } from './ProtectedRoute'

/** Renders the current pathname plus any router state, for redirect assertions. */
function LocationProbe({ label }: { label: string }) {
  const location = useLocation()
  const from = (location.state as { from?: { pathname: string } } | null)?.from
  return (
    <div>
      <span>{label}</span>
      <span data-testid="pathname">{location.pathname}</span>
      <span data-testid="from">{from?.pathname ?? ''}</span>
    </div>
  )
}

/**
 * A miniature router covering the three role homes plus /login, so a redirect
 * lands somewhere observable instead of a blank match.
 */
function renderAt(route: string, element: React.ReactElement) {
  return render(
    <Routes>
      <Route path="/login" element={<LocationProbe label="login page" />} />
      <Route path="/explore" element={<LocationProbe label="explore page" />} />
      <Route path="/dashboard" element={<LocationProbe label="advisor dashboard" />} />
      <Route path="/admin" element={<LocationProbe label="admin dashboard" />} />
      <Route path="/protected" element={element} />
    </Routes>,
    { route },
  )
}

const PROTECTED_CONTENT = <p>protected content</p>

describe('ProtectedRoute', () => {
  describe('when unauthenticated', () => {
    it('redirects to /login', () => {
      renderAt('/protected', <ProtectedRoute>{PROTECTED_CONTENT}</ProtectedRoute>)

      expect(screen.getByTestId('pathname')).toHaveTextContent('/login')
      expect(screen.queryByText('protected content')).not.toBeInTheDocument()
    })

    it('records where the visitor was headed so login can bounce them back', () => {
      renderAt('/protected', <ProtectedRoute>{PROTECTED_CONTENT}</ProtectedRoute>)

      expect(screen.getByTestId('from')).toHaveTextContent('/protected')
    })

    it('redirects to /login even when allowedRoles would have matched', () => {
      // role is set but isAuthenticated is false: corrupted persisted state.
      seedAuthStore({ role: 'admin', isAuthenticated: false })

      renderAt(
        '/protected',
        <ProtectedRoute allowedRoles={['admin']}>{PROTECTED_CONTENT}</ProtectedRoute>,
      )

      expect(screen.getByTestId('pathname')).toHaveTextContent('/login')
    })
  })

  describe('when authenticated with the wrong role', () => {
    it('sends a plain user to /explore', () => {
      seedAuthStore({ isAuthenticated: true, role: 'user' })

      renderAt(
        '/protected',
        <ProtectedRoute allowedRoles={['admin']}>{PROTECTED_CONTENT}</ProtectedRoute>,
      )

      expect(screen.getByText('explore page')).toBeInTheDocument()
      expect(screen.queryByText('protected content')).not.toBeInTheDocument()
    })

    it('sends an advisor to /dashboard', () => {
      seedAuthStore({ isAuthenticated: true, role: 'advisor' })

      renderAt(
        '/protected',
        <ProtectedRoute allowedRoles={['admin']}>{PROTECTED_CONTENT}</ProtectedRoute>,
      )

      expect(screen.getByText('advisor dashboard')).toBeInTheDocument()
    })

    it('sends an admin to /admin', () => {
      seedAuthStore({ isAuthenticated: true, role: 'admin' })

      renderAt(
        '/protected',
        <ProtectedRoute allowedRoles={['advisor']}>{PROTECTED_CONTENT}</ProtectedRoute>,
      )

      expect(screen.getByText('admin dashboard')).toBeInTheDocument()
    })

    it('falls back to /login when the session has no role at all', () => {
      seedAuthStore({ isAuthenticated: true, role: null })

      renderAt(
        '/protected',
        <ProtectedRoute allowedRoles={['admin']}>{PROTECTED_CONTENT}</ProtectedRoute>,
      )

      expect(screen.getByText('login page')).toBeInTheDocument()
    })
  })

  describe('when authenticated with an allowed role', () => {
    it('renders children', () => {
      seedAuthStore({ isAuthenticated: true, role: 'admin' })

      renderAt(
        '/protected',
        <ProtectedRoute allowedRoles={['admin']}>{PROTECTED_CONTENT}</ProtectedRoute>,
      )

      expect(screen.getByText('protected content')).toBeInTheDocument()
    })

    it('accepts any role listed in allowedRoles', () => {
      seedAuthStore({ isAuthenticated: true, role: 'advisor' })

      renderAt(
        '/protected',
        <ProtectedRoute allowedRoles={['advisor', 'admin']}>{PROTECTED_CONTENT}</ProtectedRoute>,
      )

      expect(screen.getByText('protected content')).toBeInTheDocument()
    })

    it('requires only authentication when allowedRoles is omitted', () => {
      seedAuthStore({ isAuthenticated: true, role: 'user' })

      renderAt('/protected', <ProtectedRoute>{PROTECTED_CONTENT}</ProtectedRoute>)

      expect(screen.getByText('protected content')).toBeInTheDocument()
    })

    it('treats an empty allowedRoles array as "any signed-in role"', () => {
      seedAuthStore({ isAuthenticated: true, role: 'user' })

      renderAt('/protected', <ProtectedRoute allowedRoles={[]}>{PROTECTED_CONTENT}</ProtectedRoute>)

      expect(screen.getByText('protected content')).toBeInTheDocument()
    })
  })
})
