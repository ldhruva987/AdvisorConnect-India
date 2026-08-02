import { useMutation, useQueryClient, type UseMutationResult } from '@tanstack/react-query'
import apiClient from '@/lib/axios'
import { notificationsQueryKey } from './useNotifications'
import type { NotificationDto } from '@/types/api'

/** `PUT /notifications/{id}/read` — pending backend Phase 6. */
export async function putNotificationRead(id: string): Promise<NotificationDto> {
  const { data } = await apiClient.put<NotificationDto>(
    `/notifications/${encodeURIComponent(id)}/read`,
  )
  return data
}

/**
 * Mark one notification as read.
 *
 * Optimistic: the unread dot clears the instant the user opens the item rather
 * than a round trip later, which for a UI affordance this small is the
 * difference between feeling instant and feeling broken. The snapshot taken in
 * `onMutate` is restored if the request fails, and `onSettled` refetches so
 * the server stays authoritative either way.
 */
export function useMarkNotificationRead(): UseMutationResult<
  NotificationDto,
  Error,
  string,
  { previous: NotificationDto[] | undefined }
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: putNotificationRead,

    onMutate: async (id) => {
      // Stop an in-flight poll from landing on top of the optimistic update.
      await queryClient.cancelQueries({ queryKey: notificationsQueryKey() })

      const previous = queryClient.getQueryData<NotificationDto[]>(notificationsQueryKey())

      queryClient.setQueryData<NotificationDto[]>(notificationsQueryKey(), (current) =>
        current?.map((n) => (n.id === id ? { ...n, read: true } : n)),
      )

      return { previous }
    },

    onError: (_error, _id, context) => {
      if (context?.previous) {
        queryClient.setQueryData(notificationsQueryKey(), context.previous)
      }
    },

    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: notificationsQueryKey() })
    },
  })
}
