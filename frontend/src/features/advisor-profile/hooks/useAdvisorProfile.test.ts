import { describe, expect, it, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { server } from '@/test/mocks/server'
import { createQueryWrapper, renderHook, waitFor } from '@/test/test-utils'
import { useAdvisorProfile } from './useAdvisorProfile'

describe('useAdvisorProfile', () => {
  it('fetches and maps the advisor named in the route param', async () => {
    const { result } = renderHook(() => useAdvisorProfile('sam_okafor'), {
      wrapper: createQueryWrapper(),
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    expect(result.current.data?.username).toBe('sam_okafor')
    expect(result.current.data?.title).toBe('Licensed Therapist')
    expect(result.current.data?.sectors).toEqual(['Mental Health', 'Life Coaching'])
  })

  it('fetches a *different* advisor for a different username', async () => {
    // Guards the real routing bug this hook exists to fix: AdvisorProfilePage
    // renders one hardcoded advisor regardless of `:username`.
    const { result } = renderHook(() => useAdvisorProfile('rio_alvarez'), {
      wrapper: createQueryWrapper(),
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.username).toBe('rio_alvarez')
    expect(result.current.data?.title).toBe('Financial Planner')
  })

  it('does not fire a request while the username is undefined', async () => {
    const onRequest = vi.fn()
    server.events.on('request:start', onRequest)

    const { result } = renderHook(() => useAdvisorProfile(undefined), {
      wrapper: createQueryWrapper(),
    })

    // A disabled query is pending-but-idle, never loading.
    expect(result.current.fetchStatus).toBe('idle')
    expect(result.current.data).toBeUndefined()
    expect(onRequest).not.toHaveBeenCalled()

    server.events.removeListener('request:start', onRequest)
  })

  it('surfaces a 404 for an unknown username', async () => {
    const { result } = renderHook(() => useAdvisorProfile('nobody_here'), {
      wrapper: createQueryWrapper(),
    })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(result.current.data).toBeUndefined()
  })

  it('url-encodes the username', async () => {
    let path: string | undefined
    server.use(
      http.get('*/api/advisors/:username', ({ request }) => {
        path = new URL(request.url).pathname
        return HttpResponse.json({ id: 'x', username: 'odd name', sectors: [] })
      }),
    )

    const { result } = renderHook(() => useAdvisorProfile('odd name'), {
      wrapper: createQueryWrapper(),
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(path).toBe('/api/advisors/odd%20name')
  })
})
