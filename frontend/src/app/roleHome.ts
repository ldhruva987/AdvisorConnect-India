import type { UserRole } from '@/types'

/**
 * Where each role belongs when it lands somewhere it isn't allowed. Sending a
 * user to their own home beats a dead-end 403 page — the most common cause of
 * a role mismatch is a stale bookmark, not an attack.
 *
 * Kept out of `ProtectedRoute.tsx` so that file exports only its component:
 * mixing constant and component exports breaks React Fast Refresh (and trips
 * `react-refresh/only-export-components`).
 */
export const ROLE_HOME: Record<UserRole, string> = {
  user: '/explore',
  advisor: '/dashboard',
  admin: '/admin',
}
