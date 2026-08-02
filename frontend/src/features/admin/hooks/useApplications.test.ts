import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { server } from '@/test/mocks/server'
import { createQueryWrapper, renderHook, waitFor } from '@/test/test-utils'
import { applicationsQueryKey, useApplications } from './useApplications'

describe('applicationsQueryKey', () => {
  it('normalises an absent status to null so keys hash stably', () => {
    expect(applicationsQueryKey()).toEqual(['applications', null])
    expect(applicationsQueryKey('PENDING')).toEqual(['applications', 'PENDING'])
  })
})

describe('useApplications', () => {
  it('loads, then returns mapped applications and totals', async () => {
    const { result } = renderHook(() => useApplications(), { wrapper: createQueryWrapper() })

    expect(result.current.isLoading).toBe(true)

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.applications).toHaveLength(2)
    expect(result.current.data?.totalElements).toBe(2)
  })

  it('maps the wire DTO into the UI application shape', async () => {
    const { result } = renderHook(() => useApplications(), { wrapper: createQueryWrapper() })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    const [noor] = result.current.data!.applications
    expect(noor.title).toBe('Parenting Consultant')
    expect(noor.color).toBe('#784f00')
    expect(noor.sectors).toEqual(['Parenting'])
    expect(noor.legalName).toBe('Noor Haddad')
    expect(noor).not.toHaveProperty('professionalTitle')
    expect(noor).not.toHaveProperty('avatarColor')
  })

  it('filters by status server-side', async () => {
    const { result } = renderHook(() => useApplications('UNDER_REVIEW'), {
      wrapper: createQueryWrapper(),
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.applications.map((a) => a.username)).toEqual(['dev_kapoor'])
  })

  it('sends status and page as query params', async () => {
    let params: URLSearchParams | undefined
    server.use(
      http.get('*/api/advisors/applications', ({ request }) => {
        params = new URL(request.url).searchParams
        return HttpResponse.json({ content: [], totalElements: 0, totalPages: 0, number: 1, size: 20 })
      }),
    )

    const { result } = renderHook(() => useApplications('PENDING', 1), {
      wrapper: createQueryWrapper(),
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(params?.get('status')).toBe('PENDING')
    expect(params?.get('page')).toBe('1')
  })

  it('reports an error when the admin list endpoint rejects', async () => {
    server.use(
      http.get('*/api/advisors/applications', () =>
        HttpResponse.json({ message: 'Forbidden' }, { status: 403 }),
      ),
    )

    const { result } = renderHook(() => useApplications(), { wrapper: createQueryWrapper() })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(result.current.data).toBeUndefined()
  })
})
