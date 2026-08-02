import type { ReactElement, ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import {
  render as rtlRender,
  type RenderOptions as RtlRenderOptions,
  type RenderResult,
} from '@testing-library/react'
import { useAuthStore } from '@/stores/authStore'
import { useChatStore } from '@/stores/chatStore'
import { useToastStore } from '@/stores/toastStore'

/* -------------------------------------------------------------------------- */
/* Query client                                                               */
/* -------------------------------------------------------------------------- */

/**
 * A QueryClient tuned for tests. Mirrors the app's client in `src/app/App.tsx`
 * except that retries are off (a retried failure would otherwise hang the test
 * while TanStack Query backs off) and nothing is ever considered fresh, so each
 * test observes a real fetch against MSW rather than a warm cache.
 */
export function createTestQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false, staleTime: 0, gcTime: 0 },
      mutations: { retry: false },
    },
  })
}

/**
 * Wrapper for `renderHook` on the `src/features/**` API hooks.
 *
 * Those hooks need TanStack Query but not a router, so this is deliberately
 * narrower than `renderWithProviders` — a hook test that accidentally depended
 * on router context would be hiding a real requirement from its page.
 *
 * Pass a client in when the test needs to inspect or prime the cache (e.g.
 * `useApplicationDetail`, which reads what `useApplications` wrote):
 *
 *   const queryClient = createTestQueryClient()
 *   renderHook(() => useThing(), { wrapper: createQueryWrapper(queryClient) })
 */
export function createQueryWrapper(
  queryClient: QueryClient = createTestQueryClient(),
): ({ children }: { children: ReactNode }) => ReactElement {
  return function QueryWrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  }
}

/* -------------------------------------------------------------------------- */
/* Store seeding                                                              */
/* -------------------------------------------------------------------------- */

type AuthState = ReturnType<typeof useAuthStore.getState>
type ChatState = ReturnType<typeof useChatStore.getState>

// Captured once at module load, before any test mutates them.
const pristineAuthState = useAuthStore.getState()
const pristineChatState = useChatStore.getState()

/** Seed auth state so a component renders as an already-logged-in user. */
export function seedAuthStore(partial: Partial<AuthState>): void {
  useAuthStore.setState(partial)
}

/**
 * Convenience over `seedAuthStore` for the common "any logged-in user" case.
 * Pass overrides for role-specific tests, e.g. `loginAs({ role: 'admin' })`.
 */
export function loginAs(overrides: Partial<AuthState> = {}): void {
  seedAuthStore({
    userId: 'test-user-id',
    role: 'user',
    accessToken: 'test-access-token',
    refreshToken: 'test-refresh-token',
    isAuthenticated: true,
    ...overrides,
  })
}

/** Seed chat state (conversations / messages) ahead of a render. */
export function seedChatStore(partial: Partial<ChatState>): void {
  useChatStore.setState(partial)
}

/**
 * Restore both stores to their module-load state and drop persisted auth from
 * localStorage. Called from `src/test/setup.ts` after every test — zustand
 * stores are module singletons, so without this, state leaks between tests.
 */
export function resetStores(): void {
  useAuthStore.setState(pristineAuthState, true)
  useChatStore.setState(pristineChatState, true)
  // Toasts are additive, so a leaked one from a previous test shows up as a
  // phantom extra node in the next test's queries.
  useToastStore.getState().clear()
  localStorage.clear()
}

/* -------------------------------------------------------------------------- */
/* Render                                                                     */
/* -------------------------------------------------------------------------- */

export interface RenderWithProvidersOptions extends Omit<RtlRenderOptions, 'wrapper'> {
  /** Starting URL. Shorthand for `initialEntries: [route]`. Defaults to `/`. */
  route?: string
  /** Full history stack, for tests that assert on navigation. Wins over `route`. */
  initialEntries?: string[]
  /**
   * Route pattern to mount `ui` under, e.g. `'/advisor/:username'`. Required
   * when the component under test reads `useParams()`; without it React Router
   * has no match and params come back empty.
   */
  path?: string
  /** Supply your own client to inspect/prime the cache. */
  queryClient?: QueryClient
}

export interface RenderWithProvidersResult extends RenderResult {
  queryClient: QueryClient
}

/**
 * Render `ui` inside the providers every page in this app depends on:
 * TanStack Query and a `MemoryRouter`.
 */
export function renderWithProviders(
  ui: ReactElement,
  options: RenderWithProvidersOptions = {},
): RenderWithProvidersResult {
  const {
    route = '/',
    initialEntries,
    path,
    queryClient = createTestQueryClient(),
    ...rtlOptions
  } = options

  function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={initialEntries ?? [route]}>
          {path ? (
            <Routes>
              <Route path={path} element={children} />
            </Routes>
          ) : (
            children
          )}
        </MemoryRouter>
      </QueryClientProvider>
    )
  }

  return {
    ...rtlRender(ui, { wrapper: Wrapper, ...rtlOptions }),
    queryClient,
  }
}

/* -------------------------------------------------------------------------- */
/* Re-exports                                                                 */
/* -------------------------------------------------------------------------- */

// Standard testing-library "custom render" pattern: re-export the whole RTL
// surface, then shadow its bare `render` with the provider-wrapped one, so test
// files only ever need `import { render, screen } from '@/test/test-utils'`.
export * from '@testing-library/react'
export { renderWithProviders as render }
