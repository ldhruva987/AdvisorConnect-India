import { useMutation, type UseMutationResult } from '@tanstack/react-query'
import apiClient from '@/lib/axios'
import type { TokenResponse } from '@/types/api'

/**
 * Registration takes only an email and a password.
 *
 * There is deliberately no `role` field: backend Phase 2 locks
 * `POST /auth/register` to always create `role=USER`, closing the hole where a
 * client could self-register as `ADMIN`. Advisors are promoted via the
 * `advisor.approved` event, never by asking nicely at signup.
 */
export interface RegisterCredentials {
  email: string
  password: string
}

/** `POST /auth/register`. Real backend endpoint; auto-logs-in on success. */
export async function postRegister(credentials: RegisterCredentials): Promise<TokenResponse> {
  const { data } = await apiClient.post<TokenResponse>('/auth/register', credentials)
  return data
}

/**
 * Thin `useMutation` wrapper around `POST /auth/register`.
 *
 * As with `useLogin`, storing the returned tokens and navigating is the
 * calling page's responsibility, not this hook's — the endpoint returns a full
 * `TokenResponse` precisely so the page can call `useAuthStore.login()` with
 * it directly and skip a second round trip.
 */
export function useRegister(): UseMutationResult<TokenResponse, Error, RegisterCredentials> {
  return useMutation({ mutationFn: postRegister })
}
