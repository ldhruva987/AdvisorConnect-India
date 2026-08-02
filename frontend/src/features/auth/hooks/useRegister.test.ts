import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { getErrorMessage } from '@/lib/getErrorMessage'
import { server } from '@/test/mocks/server'
import { MOCK_REGISTER_RESPONSE, TAKEN_EMAIL } from '@/test/mocks/handlers/auth'
import { useAuthStore } from '@/stores/authStore'
import { act, createQueryWrapper, renderHook, waitFor } from '@/test/test-utils'
import { useRegister } from './useRegister'

describe('useRegister', () => {
  it('returns the auto-login token response on success', async () => {
    const { result } = renderHook(() => useRegister(), { wrapper: createQueryWrapper() })

    act(() => {
      result.current.mutate({ email: 'new@example.com', password: 'correct-horse' })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(MOCK_REGISTER_RESPONSE)
    // Registration auto-logs-in, so the page has everything it needs to call
    // `useAuthStore.login()` without a second request.
    expect(result.current.data?.accessToken).toBeTruthy()
    expect(result.current.data?.role).toBe('user')
  })

  it('posts only email and password — never a role', async () => {
    let body: Record<string, unknown> | undefined
    server.use(
      http.post('*/api/auth/register', async ({ request }) => {
        body = (await request.json()) as Record<string, unknown>
        return HttpResponse.json(MOCK_REGISTER_RESPONSE, { status: 201 })
      }),
    )

    const { result } = renderHook(() => useRegister(), { wrapper: createQueryWrapper() })

    act(() => {
      result.current.mutate({ email: 'new@example.com', password: 'correct-horse' })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    // Backend Phase 2 locks registration to role=USER. A client that could
    // send a role could self-register as ADMIN, so assert the absence.
    expect(Object.keys(body ?? {}).sort()).toEqual(['email', 'password'])
    expect(body).not.toHaveProperty('role')
  })

  it('surfaces a duplicate-email 409 with the backend message', async () => {
    const { result } = renderHook(() => useRegister(), { wrapper: createQueryWrapper() })

    act(() => {
      result.current.mutate({ email: TAKEN_EMAIL, password: 'correct-horse' })
    })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(getErrorMessage(result.current.error)).toBe('Email already registered')
  })

  it('does not write to the auth store itself', async () => {
    const { result } = renderHook(() => useRegister(), { wrapper: createQueryWrapper() })

    act(() => {
      result.current.mutate({ email: 'new@example.com', password: 'correct-horse' })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(useAuthStore.getState().isAuthenticated).toBe(false)
  })
})
