import { useMutation, type UseMutationResult } from '@tanstack/react-query'
import apiClient from '@/lib/axios'
import type { TokenResponse } from '@/types/api'

export interface LoginCredentials {
  email: string
  password: string
}

/** `POST /auth/login`. Real backend endpoint. */
export async function postLogin(credentials: LoginCredentials): Promise<TokenResponse> {
  const { data } = await apiClient.post<TokenResponse>('/auth/login', credentials)
  return data
}

/**
 * Thin `useMutation` wrapper around `POST /auth/login`.
 *
 * Deliberately does NOT call `useAuthStore.login()` or navigate on success.
 * Both are the calling page's job (Phase 2): keeping the side effects out here
 * means this hook is a pure request wrapper that can be tested without a
 * router or a seeded store, and means the same hook is reusable from a
 * re-authentication modal that shouldn't navigate anywhere.
 */
export function useLogin(): UseMutationResult<TokenResponse, Error, LoginCredentials> {
  return useMutation({ mutationFn: postLogin })
}
