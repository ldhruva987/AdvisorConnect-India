import { describe, expect, it, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { getErrorMessage } from '@/lib/getErrorMessage'
import { server } from '@/test/mocks/server'
import { UNKNOWN_APPLICATION_ID } from '@/test/mocks/handlers/admin'
import {
  act,
  createQueryWrapper,
  createTestQueryClient,
  renderHook,
  waitFor,
} from '@/test/test-utils'
import { useApplicationDecision } from './useApplicationDecision'

describe('useApplicationDecision', () => {
  it('PUTs to the approve path with the notes body', async () => {
    let path: string | undefined
    let body: unknown
    server.use(
      http.put('*/api/advisors/applications/:id/approve', async ({ request }) => {
        path = new URL(request.url).pathname
        body = await request.json()
        return new HttpResponse(null, { status: 204 })
      }),
    )

    const { result } = renderHook(() => useApplicationDecision(), {
      wrapper: createQueryWrapper(),
    })

    act(() => {
      result.current.mutate({ id: 'application-1', decision: 'approve', notes: 'Credentials check out.' })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(path).toBe('/api/advisors/applications/application-1/approve')
    expect(body).toEqual({ notes: 'Credentials check out.' })
  })

  it('PUTs to the reject path for a rejection', async () => {
    let path: string | undefined
    server.use(
      http.put('*/api/advisors/applications/:id/reject', ({ request }) => {
        path = new URL(request.url).pathname
        return new HttpResponse(null, { status: 204 })
      }),
    )

    const { result } = renderHook(() => useApplicationDecision(), {
      wrapper: createQueryWrapper(),
    })

    act(() => {
      result.current.mutate({ id: 'application-2', decision: 'reject', notes: 'Insufficient docs.' })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(path).toBe('/api/advisors/applications/application-2/reject')
  })

  it('sends an empty string when notes are omitted', async () => {
    let body: unknown
    server.use(
      http.put('*/api/advisors/applications/:id/approve', async ({ request }) => {
        body = await request.json()
        return new HttpResponse(null, { status: 204 })
      }),
    )

    const { result } = renderHook(() => useApplicationDecision(), {
      wrapper: createQueryWrapper(),
    })

    act(() => {
      result.current.mutate({ id: 'application-1', decision: 'approve' })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    // The backend always reads a `notes` field; omitting the key entirely
    // would deserialise to null.
    expect(body).toEqual({ notes: '' })
  })

  it('invalidates the application list and the admin stats', async () => {
    const queryClient = createTestQueryClient()
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries')

    const { result } = renderHook(() => useApplicationDecision(), {
      wrapper: createQueryWrapper(queryClient),
    })

    act(() => {
      result.current.mutate({ id: 'application-1', decision: 'approve' })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['applications'] })
    // Approving moves a record out of pendingApplications and into the
    // advisor count, so the tiles are stale too.
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['admin', 'stats'] })
  })

  it('surfaces a 404 for an unknown application', async () => {
    const { result } = renderHook(() => useApplicationDecision(), {
      wrapper: createQueryWrapper(),
    })

    act(() => {
      result.current.mutate({ id: UNKNOWN_APPLICATION_ID, decision: 'approve' })
    })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(getErrorMessage(result.current.error)).toBe('Application not found')
  })

  it('surfaces a 403 when the caller is not an admin', async () => {
    server.use(
      http.put('*/api/advisors/applications/:id/approve', () =>
        HttpResponse.json({ message: 'Access denied' }, { status: 403 }),
      ),
    )

    const { result } = renderHook(() => useApplicationDecision(), {
      wrapper: createQueryWrapper(),
    })

    act(() => {
      result.current.mutate({ id: 'application-1', decision: 'approve' })
    })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(getErrorMessage(result.current.error)).toBe('Access denied')
  })
})
