import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { UserRole } from '@/types'

interface AuthStore {
  userId: string | null
  role: UserRole | null
  accessToken: string | null
  refreshToken: string | null
  isAuthenticated: boolean
  // actions
  login: (params: { userId: string; role: UserRole; accessToken: string; refreshToken: string }) => void
  logout: () => void
  setTokens: (accessToken: string, refreshToken: string) => void
}

export const useAuthStore = create<AuthStore>()(
  persist(
    (set) => ({
      userId: null,
      role: null,
      accessToken: null,
      refreshToken: null,
      isAuthenticated: false,

      login: ({ userId, role, accessToken, refreshToken }) =>
        set({ userId, role, accessToken, refreshToken, isAuthenticated: true }),

      logout: () =>
        set({ userId: null, role: null, accessToken: null, refreshToken: null, isAuthenticated: false }),

      setTokens: (accessToken, refreshToken) =>
        set({ accessToken, refreshToken }),
    }),
    { name: 'ac-auth', partialize: (s) => ({ accessToken: s.accessToken, refreshToken: s.refreshToken, userId: s.userId, role: s.role, isAuthenticated: s.isAuthenticated }) },
  ),
)
