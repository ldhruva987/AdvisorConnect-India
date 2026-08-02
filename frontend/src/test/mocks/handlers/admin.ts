import { http, HttpResponse } from 'msw'
import type { AdminStatsDto } from '@/types/api'

/**
 * Admin stats + application approve/reject.
 *
 * The approve/reject endpoints are REAL today (advisor-service, `204`-style
 * void returns), so these handlers mirror that exactly: an empty body, not a
 * fabricated success envelope.
 *
 * `GET /admin/stats` deliberately returns only the two fields the backend
 * actually computes today, so `useAdminStats` is exercised against the real
 * partial response rather than a fixture that pretends Phase 10 already landed.
 */

export const MOCK_ADMIN_STATS_DTO: AdminStatsDto = {
  approvedAdvisors: 42,
  totalAdminActions: 117,
}

/** Application id the decision endpoints reject, for failure-path tests. */
export const UNKNOWN_APPLICATION_ID = 'application-missing'

export const adminHandlers = [
  http.get('*/api/admin/stats', () => HttpResponse.json(MOCK_ADMIN_STATS_DTO)),

  http.put('*/api/advisors/applications/:id/approve', ({ params }) => {
    if (params.id === UNKNOWN_APPLICATION_ID) {
      return HttpResponse.json({ message: 'Application not found' }, { status: 404 })
    }
    return new HttpResponse(null, { status: 204 })
  }),

  http.put('*/api/advisors/applications/:id/reject', ({ params }) => {
    if (params.id === UNKNOWN_APPLICATION_ID) {
      return HttpResponse.json({ message: 'Application not found' }, { status: 404 })
    }
    return new HttpResponse(null, { status: 204 })
  }),
]
