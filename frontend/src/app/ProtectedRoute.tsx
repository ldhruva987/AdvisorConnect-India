import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuthStore } from '@/stores/authStore'
import { ROLE_HOME } from './roleHome'
import type { UserRole } from '@/types'

interface ProtectedRouteProps {
  children: ReactNode
  /** Omit to require only that someone is signed in. */
  allowedRoles?: UserRole[]
}

/**
 * Route guard, wired into `router.tsx` around `/dashboard` and `/admin`.
 *
 * This is a UX guard, not a security boundary: the API gateway still
 * authorises every request. It exists so people don't see a broken shell of a
 * page they can't populate.
 */
export function ProtectedRoute({ children, allowedRoles }: ProtectedRouteProps) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  const role = useAuthStore((s) => s.role)
  const location = useLocation()

  if (!isAuthenticated) {
    // `from` lets LoginPage bounce back to the intended page after sign-in.
    return <Navigate to="/login" replace state={{ from: location }} />
  }

  if (allowedRoles && allowedRoles.length > 0 && (!role || !allowedRoles.includes(role))) {
    // Authenticated but out of bounds. A missing role on an authenticated
    // session means corrupted persisted state — treat it as signed out.
    return <Navigate to={role ? ROLE_HOME[role] : '/login'} replace />
  }

  return <>{children}</>
}
