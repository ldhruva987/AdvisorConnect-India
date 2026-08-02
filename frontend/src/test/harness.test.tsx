import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useAuthStore } from '@/stores/authStore'
import { server } from './mocks/server'
import { loginAs, render, screen, seedAuthStore, waitFor } from './test-utils'

/**
 * Smoke tests for the Phase 0 test harness itself. These assert the *tooling*
 * works — jsdom, the `@` alias, jest-dom matchers, MSW interception, the
 * QueryClient wrapper, router params, and store seeding/reset — so that when a
 * real feature test fails in a later phase, it's the feature that's broken and
 * not the scaffolding underneath it.
 */

describe('test harness', () => {
  it('runs in a jsdom environment with jest-dom matchers registered', () => {
    render(<p>hello harness</p>)
    // `toBeInTheDocument` only exists if @testing-library/jest-dom loaded.
    expect(screen.getByText('hello harness')).toBeInTheDocument()
  })

  it('resolves the @ path alias to src', () => {
    expect(typeof useAuthStore.getState().login).toBe('function')
  })

  it('exposes router params to the component under test', () => {
    function ShowParam() {
      const { username } = useParams()
      return <span>advisor: {username}</span>
    }

    render(<ShowParam />, { path: '/advisor/:username', route: '/advisor/jane-doe' })

    expect(screen.getByText('advisor: jane-doe')).toBeInTheDocument()
  })

  it('intercepts network calls through MSW and resolves them via React Query', async () => {
    server.use(
      http.get('https://example.test/ping', () => HttpResponse.json({ status: 'pong' })),
    )

    function Ping() {
      const { data } = useQuery({
        queryKey: ['ping'],
        queryFn: async () => {
          const res = await fetch('https://example.test/ping')
          return (await res.json()) as { status: string }
        },
      })
      return <span>{data?.status ?? 'loading'}</span>
    }

    render(<Ping />)

    await waitFor(() => expect(screen.getByText('pong')).toBeInTheDocument())
  })

  it('seeds auth state so components can render as a logged-in user', () => {
    loginAs({ role: 'admin' })

    const state = useAuthStore.getState()
    expect(state.isAuthenticated).toBe(true)
    expect(state.role).toBe('admin')
  })

  it('resets store state between tests', () => {
    // Depends on the previous test having called loginAs({ role: 'admin' }).
    expect(useAuthStore.getState().isAuthenticated).toBe(false)
    expect(useAuthStore.getState().role).toBeNull()

    seedAuthStore({ userId: 'abc' })
    expect(useAuthStore.getState().userId).toBe('abc')
  })
})
