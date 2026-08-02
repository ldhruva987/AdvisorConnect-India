import axios from 'axios'
import { useAuthStore } from '@/stores'

const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_GATEWAY_URL ?? '/api',
  headers: { 'Content-Type': 'application/json' },
  timeout: 15_000,
})

// ── Request interceptor: attach JWT ──────────────────────────────
apiClient.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// ── Response interceptor: handle 401 → token refresh ─────────────
let isRefreshing = false
let failedQueue: Array<{ resolve: (v: string) => void; reject: (e: unknown) => void }> = []

const processQueue = (error: unknown, token: string | null) => {
  failedQueue.forEach((prom) => (error ? prom.reject(error) : prom.resolve(token!)))
  failedQueue = []
}

/**
 * Endpoints where a 401 means "these credentials are wrong", not "your session
 * expired". Refreshing in response to a failed login is meaningless — there is
 * no session yet — and actively harmful: the refresh attempt's own failure
 * replaces the real error, so the user is told "cannot reach the server"
 * instead of "invalid email or password".
 */
const NO_REFRESH_PATHS = ['/auth/login', '/auth/register', '/auth/refresh']

const shouldSkipRefresh = (url: string | undefined) =>
  !!url && NO_REFRESH_PATHS.some((path) => url.startsWith(path) || url.includes(`/api${path}`))

apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config
    if (error.response?.status !== 401 || !originalRequest || originalRequest._retry) {
      return Promise.reject(error)
    }
    if (shouldSkipRefresh(originalRequest.url)) return Promise.reject(error)
    // Nothing to refresh with: fail fast rather than POST `{refreshToken: null}`
    // and log the user out on the resulting error.
    if (!useAuthStore.getState().refreshToken) return Promise.reject(error)

    if (isRefreshing) {
      return new Promise((resolve, reject) => {
        failedQueue.push({ resolve, reject })
      }).then((token) => {
        originalRequest.headers.Authorization = `Bearer ${token}`
        return apiClient(originalRequest)
      })
    }

    originalRequest._retry = true
    isRefreshing = true

    try {
      const refreshToken = useAuthStore.getState().refreshToken
      const { data } = await axios.post('/api/auth/refresh', { refreshToken })
      useAuthStore.getState().setTokens(data.accessToken, data.refreshToken)
      processQueue(null, data.accessToken)
      originalRequest.headers.Authorization = `Bearer ${data.accessToken}`
      return apiClient(originalRequest)
    } catch (refreshError) {
      processQueue(refreshError, null)
      useAuthStore.getState().logout()
      return Promise.reject(refreshError)
    } finally {
      isRefreshing = false
    }
  },
)

export default apiClient
