import type { RequestHandler } from 'msw'
import { adminHandlers } from './handlers/admin'
import { advisorHandlers } from './handlers/advisors'
import { authHandlers } from './handlers/auth'
import { bookingHandlers } from './handlers/bookings'
import { chatHandlers } from './handlers/chat'
import { notificationHandlers } from './handlers/notifications'
import { uploadHandlers } from './handlers/upload'

/**
 * Root MSW handler list — one file per feature, mirroring the real and pending
 * backend contracts.
 *
 * The server runs with `onUnhandledRequest: 'error'`, so any call a hook makes
 * that isn't mocked here fails the test loudly rather than escaping to the
 * network. Individual tests override a handler with `server.use(...)` to drive
 * error paths; `server.resetHandlers()` in `setup.ts` undoes that per test.
 *
 * ORDER IS SIGNIFICANT. MSW resolves with the first matching handler, and the
 * advisor routes are genuinely ambiguous: `/advisors/applications` would be
 * captured by `/advisors/:username` if the latter came first. `advisorHandlers`
 * keeps its own internal ordering to handle that; keep it ahead of anything
 * that adds further `/advisors/*` routes.
 *
 * Every path is written with a leading star wildcard, i.e. star + `/api/...`,
 * rather than a bare relative path. `apiClient`'s baseURL is the relative
 * `/api`, which jsdom resolves against its own origin; the wildcard matches
 * that without hardcoding whatever host jsdom happens to be configured with.
 */
export const handlers: RequestHandler[] = [
  ...authHandlers,
  ...advisorHandlers,
  ...bookingHandlers,
  ...chatHandlers,
  ...adminHandlers,
  ...notificationHandlers,
  ...uploadHandlers,
]
