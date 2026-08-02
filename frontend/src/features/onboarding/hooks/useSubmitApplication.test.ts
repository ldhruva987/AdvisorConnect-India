import { describe, expect, it, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { getErrorMessage } from '@/lib/getErrorMessage'
import { server } from '@/test/mocks/server'
import type { SubmitApplicationRequest } from '@/types/api'
import {
  act,
  createQueryWrapper,
  createTestQueryClient,
  renderHook,
  waitFor,
} from '@/test/test-utils'
import { useSubmitApplication } from './useSubmitApplication'

const APPLICATION: SubmitApplicationRequest = {
  username: 'noor_haddad',
  professionalTitle: 'Parenting Consultant',
  bio: 'x'.repeat(60),
  sectors: ['PARENTING'],
  qualification: 'MA Child Development',
  fieldOfStudy: 'Child Development',
  experienceYears: '5-10',
  previousWork: 'Community family centre.',
  legalFirstName: 'Noor',
  legalLastName: 'Haddad',
  dateOfBirth: '1988-04-02',
  addressFull: '1 Example Street',
  country: 'IE',
  documentS3Keys: ['applications/user-1/id.pdf'],
}

describe('useSubmitApplication', () => {
  it('returns the new application id', async () => {
    const { result } = renderHook(() => useSubmitApplication(), { wrapper: createQueryWrapper() })

    act(() => {
      result.current.mutate(APPLICATION)
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toBe('application-new')
  })

  it('posts the full application body including the document keys', async () => {
    let body: SubmitApplicationRequest | undefined
    server.use(
      http.post('*/api/advisors/apply', async ({ request }) => {
        body = (await request.json()) as SubmitApplicationRequest
        return HttpResponse.json('application-new', { status: 201 })
      }),
    )

    const { result } = renderHook(() => useSubmitApplication(), { wrapper: createQueryWrapper() })

    act(() => {
      result.current.mutate(APPLICATION)
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(body).toEqual(APPLICATION)
    // The backend requires a non-empty key list — files are uploaded ahead of
    // this call and only their keys are submitted.
    expect(body?.documentS3Keys).toHaveLength(1)
    // Sectors go over the wire as backend enums, not display labels.
    expect(body?.sectors).toEqual(['PARENTING'])
  })

  it('invalidates the admin application queue', async () => {
    const queryClient = createTestQueryClient()
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries')

    const { result } = renderHook(() => useSubmitApplication(), {
      wrapper: createQueryWrapper(queryClient),
    })

    act(() => {
      result.current.mutate(APPLICATION)
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['applications'] })
  })

  it('surfaces a validation rejection from the backend', async () => {
    server.use(
      http.post('*/api/advisors/apply', () =>
        HttpResponse.json({ message: 'Bio must be at least 50 characters' }, { status: 400 }),
      ),
    )

    const { result } = renderHook(() => useSubmitApplication(), { wrapper: createQueryWrapper() })

    act(() => {
      result.current.mutate({ ...APPLICATION, bio: 'too short' })
    })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(getErrorMessage(result.current.error)).toBe('Bio must be at least 50 characters')
  })
})
