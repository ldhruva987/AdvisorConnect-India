import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { server } from '@/test/mocks/server'
import { createQueryWrapper, renderHook, waitFor } from '@/test/test-utils'
import { toAdminStats, useAdminStats } from './useAdminStats'

describe('toAdminStats', () => {
  it('widens today’s two-field response into the full dashboard shape', () => {
    const stats = toAdminStats({ approvedAdvisors: 42, totalAdminActions: 117 })

    // Every key the UI reads is present and numeric — never undefined.
    expect(stats).toEqual({
      pendingApplications: 0,
      activeAdvisors: 42,
      totalUsers: 0,
      platformRevenue: 0,
      pendingApplicationsDelta: '',
      activeAdvisorsDelta: '',
      totalUsersDelta: '',
      platformRevenueDelta: '',
    })
  })

  it('prefers a real activeAdvisors count over the approvedAdvisors stand-in', () => {
    // Once backend Phase 10 ships the real counter, the stopgap must yield.
    const stats = toAdminStats({ approvedAdvisors: 42, activeAdvisors: 39 })
    expect(stats.activeAdvisors).toBe(39)
  })

  it('passes through the full Phase 10 response unchanged', () => {
    const stats = toAdminStats({
      pendingApplications: 7,
      activeAdvisors: 39,
      totalUsers: 1204,
      platformRevenue: 815000,
      pendingApplicationsDelta: '+2',
      activeAdvisorsDelta: '+5',
      totalUsersDelta: '+120',
      platformRevenueDelta: '+12%',
    })

    expect(stats.pendingApplications).toBe(7)
    expect(stats.totalUsers).toBe(1204)
    expect(stats.platformRevenue).toBe(815000)
    expect(stats.platformRevenueDelta).toBe('+12%')
  })

  it('claims no trend it cannot support', () => {
    // Empty deltas mean "unknown", which the UI renders as no chip. Inventing
    // a "+0%" would be asserting a trend we have not measured.
    const stats = toAdminStats({})
    expect(stats.pendingApplicationsDelta).toBe('')
    expect(stats.platformRevenueDelta).toBe('')
  })
})

describe('useAdminStats', () => {
  it('loads, then returns the widened stats', async () => {
    const { result } = renderHook(() => useAdminStats(), { wrapper: createQueryWrapper() })

    expect(result.current.isLoading).toBe(true)

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.activeAdvisors).toBe(42)
    expect(result.current.data?.totalUsers).toBe(0)
  })

  it('reports an error when the stats endpoint fails', async () => {
    server.use(http.get('*/api/admin/stats', () => new HttpResponse(null, { status: 500 })))

    const { result } = renderHook(() => useAdminStats(), { wrapper: createQueryWrapper() })

    await waitFor(() => expect(result.current.isError).toBe(true))
  })
})
