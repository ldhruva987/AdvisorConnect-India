import { useQuery } from '@tanstack/react-query'
import apiClient from '@/lib/axios'
import type { NotificationDto } from '@/types/api'

/**
 * How often the bell re-checks. Polling rather than a WebSocket channel is the
 * plan's deliberate default: notifications are low-volume and latency-tolerant,
 * and a second socket would mean a second authenticated handshake to maintain
 * for something a 30-second poll covers.
 */
export const NOTIFICATIONS_POLL_INTERVAL_MS = 30_000

export const notificationsQueryKey = () => ['notifications'] as const

/** `GET /notifications` — pending backend Phase 6. */
export async function fetchNotifications(): Promise<NotificationDto[]> {
  const { data } = await apiClient.get<NotificationDto[]>('/notifications')
  return data ?? []
}

/** The notification inbox behind the Navbar bell. */
export function useNotifications() {
  return useQuery({
    queryKey: notificationsQueryKey(),
    queryFn: fetchNotifications,
    refetchInterval: NOTIFICATIONS_POLL_INTERVAL_MS,
  })
}
