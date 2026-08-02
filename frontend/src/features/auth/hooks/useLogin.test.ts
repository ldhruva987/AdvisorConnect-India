import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { getErrorMessage } from '@/lib/getErrorMessage'
import { server } from '@/test/mocks/server'
import { MOCK_LOGIN_RESPONSE, REJECTED_PASSWORD } from '@/test/mocks/handlers/auth'
import { useAuthStore } from '@/stores/authStore'
import { act, createQueryWrapper, renderHook, waitFor } from '@/test/test-utils'
import { useLogin } from './useLogin'

describe('useLogin', () => {
  it('returns the token response on success', async () => {
    const { result } = renderHook(() => useLogin(), { wrapper: createQueryWrapper() })

    expect(result.current.isPending).toBe(false)

    act(() => {
      result.current.mutate({ email: 'someone@example.com', password: 'correct-horse' })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(MOCK_LOGIN_RESPONSE)
  })

  it('sends the submitted credentials as the request body', async () => {
    let body: unknown
    server.use(
      http.post('*/api/auth/login', async ({ request }) => {
        body = await request.json()
        return HttpResponse.json(MOCK_LOGIN_RESPONSE)
      }),
    )

    const { result } = renderHook(() => useLogin(), { wrapper: createQueryWrapper() })

    act(() => {
      result.current.mutate({ email: 'someone@example.com', password: 'correct-horse' })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(body).toEqual({ email: 'someone@example.com', password: 'correct-horse' })
  })

  it('surfaces a 401 as an error with the backend message intact', async () => {
    const { result } = renderHook(() => useLogin(), { wrapper: createQueryWrapper() })

    act(() => {
      result.current.mutate({ email: 'someone@example.com', password: REJECTED_PASSWORD })
    })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(result.current.data).toBeUndefined()
    expect(getErrorMessage(result.current.error)).toBe('Invalid email or password')
  })

  it('surfaces a 500 without leaking the raw Axios message', async () => {
    server.use(http.post('*/api/auth/login', () => new HttpResponse(null, { status: 500 })))

    const { result } = renderHook(() => useLogin(), { wrapper: createQueryWrapper() })

    act(() => {
      result.current.mutate({ email: 'someone@example.com', password: 'correct-horse' })
    })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(getErrorMessage(result.current.error)).toBe('Something went wrong. Please try again.')
  })

  it('does not log the user in itself — that is the calling page’s job', async () => {
    const { result } = renderHook(() => useLogin(), { wrapper: createQueryWrapper() })

    act(() => {
      result.current.mutate({ email: 'someone@example.com', password: 'correct-horse' })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    // The hook is a thin request wrapper; it must not touch global auth state.
    expect(useAuthStore.getState().isAuthenticated).toBe(false)
    expect(useAuthStore.getState().accessToken).toBeNull()
  })
})
