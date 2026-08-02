import { http, HttpResponse } from 'msw'
import type { NotificationDto } from '@/types/api'

/**
 * Notification inbox + mark-as-read — pending backend Phase 6 (today
 * notification-service is fire-and-forget email only, with no persisted
 * entity and no REST surface at all).
 */

export const MOCK_NOTIFICATION_DTOS: NotificationDto[] = [
  {
    id: 'notification-1',
    userId: 'user-1',
    type: 'BOOKING_CONFIRMED',
    title: 'Session confirmed',
    body: 'Your session with maya_chen is confirmed for 14 August.',
    read: false,
    createdAt: '2026-08-01T09:00:00Z',
  },
  {
    id: 'notification-2',
    userId: 'user-1',
    type: 'MESSAGE_RECEIVED',
    title: 'New message',
    body: 'sam_okafor replied to your message.',
    read: true,
    createdAt: '2026-07-28T20:15:00Z',
  },
]

export const notificationHandlers = [
  http.get('*/api/notifications', () => HttpResponse.json(MOCK_NOTIFICATION_DTOS)),

  http.put('*/api/notifications/:id/read', ({ params }) => {
    const notification = MOCK_NOTIFICATION_DTOS.find((n) => n.id === params.id)
    if (!notification) {
      return HttpResponse.json({ message: 'Notification not found' }, { status: 404 })
    }
    return HttpResponse.json({ ...notification, read: true })
  }),
]
