import { describe, expect, it } from 'vitest'
import {
  createQueryWrapper,
  createTestQueryClient,
  renderHook,
  waitFor,
} from '@/test/test-utils'
import { useApplications } from './useApplications'
import { useApplicationDetail } from './useApplicationDetail'

describe('useApplicationDetail', () => {
  /** Prime the cache the way the real dashboard does: by rendering the list. */
  async function withLoadedList() {
    const queryClient = createTestQueryClient()
    const wrapper = createQueryWrapper(queryClient)
    const list = renderHook(() => useApplications(), { wrapper })
    await waitFor(() => expect(list.result.current.isSuccess).toBe(true))
    return { queryClient, wrapper }
  }

  it('finds the application matching the id', async () => {
    const { wrapper } = await withLoadedList()

    const { result } = renderHook(() => useApplicationDetail('application-2'), { wrapper })

    expect(result.current.isAvailable).toBe(true)
    expect(result.current.application?.username).toBe('dev_kapoor')
  })

  it('returns a *different* record for a different id', async () => {
    // The bug this replaces: AdminDashboardPage always renders
    // MOCK_APPLICATIONS[0] regardless of which row was clicked.
    const { wrapper } = await withLoadedList()

    const first = renderHook(() => useApplicationDetail('application-1'), { wrapper })
    const second = renderHook(() => useApplicationDetail('application-2'), { wrapper })

    expect(first.result.current.application?.username).toBe('noor_haddad')
    expect(second.result.current.application?.username).toBe('dev_kapoor')
    expect(first.result.current.application?.id).not.toBe(second.result.current.application?.id)
  })

  it('returns the mapped UI shape, not the raw DTO', async () => {
    const { wrapper } = await withLoadedList()

    const { result } = renderHook(() => useApplicationDetail('application-1'), { wrapper })

    expect(result.current.application?.title).toBe('Parenting Consultant')
    expect(result.current.application?.sectors).toEqual(['Parenting'])
  })

  it('reports unavailable when the list has not been fetched yet', () => {
    const { result } = renderHook(() => useApplicationDetail('application-1'), {
      wrapper: createQueryWrapper(),
    })

    expect(result.current.application).toBeNull()
    expect(result.current.isAvailable).toBe(false)
  })

  it('reports unavailable for an id that is on no loaded page', async () => {
    const { wrapper } = await withLoadedList()

    const { result } = renderHook(() => useApplicationDetail('application-999'), { wrapper })

    expect(result.current.application).toBeNull()
    expect(result.current.isAvailable).toBe(false)
  })

  it('reports unavailable when no id is selected', async () => {
    const { wrapper } = await withLoadedList()

    const { result } = renderHook(() => useApplicationDetail(undefined), { wrapper })

    expect(result.current.application).toBeNull()
    expect(result.current.isAvailable).toBe(false)
  })

  it('matches the status-scoped cache entry the list wrote', async () => {
    const queryClient = createTestQueryClient()
    const wrapper = createQueryWrapper(queryClient)

    const list = renderHook(() => useApplications('UNDER_REVIEW'), { wrapper })
    await waitFor(() => expect(list.result.current.isSuccess).toBe(true))

    const found = renderHook(() => useApplicationDetail('application-2', 'UNDER_REVIEW'), {
      wrapper,
    })
    expect(found.result.current.application?.username).toBe('dev_kapoor')

    // A different status filter is a different cache entry, and the
    // UNDER_REVIEW record is not in it.
    const missing = renderHook(() => useApplicationDetail('application-2', 'PENDING'), { wrapper })
    expect(missing.result.current.application).toBeNull()
  })
})
