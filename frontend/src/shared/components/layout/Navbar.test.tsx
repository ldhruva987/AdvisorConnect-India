import { describe, expect, it } from 'vitest'
import { delay, http, HttpResponse } from 'msw'
import userEvent from '@testing-library/user-event'
import { server } from '@/test/mocks/server'
import { MOCK_NOTIFICATION_DTOS } from '@/test/mocks/handlers/notifications'
import { loginAs, render, screen, waitFor, within } from '@/test/test-utils'
import type { NotificationDto } from '@/types/api'
import { Navbar } from './Navbar'

const [UNREAD, READ] = MOCK_NOTIFICATION_DTOS

function notification(overrides: Partial<NotificationDto> = {}): NotificationDto {
  return {
    id: 'notification-x',
    userId: 'user-1',
    type: 'BOOKING_CONFIRMED',
    title: 'Session confirmed',
    body: 'Your session is confirmed.',
    read: false,
    createdAt: '2026-08-01T09:00:00Z',
    ...overrides,
  }
}

function serveNotifications(notifications: NotificationDto[]) {
  server.use(http.get('*/api/notifications', () => HttpResponse.json(notifications)))
}

/**
 * Renders the Navbar next to an unrelated element, so outside-click dismissal
 * has something real to click that isn't the dropdown.
 */
function renderNavbar() {
  return render(
    <>
      <Navbar />
      <button type="button">somewhere else</button>
    </>,
  )
}

/** The bell, whichever unread state its accessible name is currently in. */
function bell() {
  return screen.getByRole('button', { name: /^notifications/i })
}

describe('Navbar', () => {
  describe('signed out', () => {
    it('shows the auth CTAs and neither the bell nor My Bookings', () => {
      renderNavbar()

      expect(screen.getByRole('link', { name: /log in/i })).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: /^notifications/i })).not.toBeInTheDocument()
      expect(screen.queryByRole('link', { name: 'My Bookings' })).not.toBeInTheDocument()
    })
  })

  describe('signed in', () => {
    it('links to /bookings for every authenticated role', async () => {
      loginAs({ role: 'advisor' })
      renderNavbar()

      expect(screen.getByRole('link', { name: 'My Bookings' })).toHaveAttribute('href', '/bookings')
      // The bell's poll is in flight; let it settle so it can't leak into the
      // next test as an act() warning.
      await screen.findByRole('button', { name: /1 unread/i })
    })
  })

  describe('notifications bell', () => {
    it('badges the unread count from useNotifications', async () => {
      serveNotifications([
        notification({ id: 'a', read: false }),
        notification({ id: 'b', read: false }),
        notification({ id: 'c', read: true }),
      ])
      loginAs()
      renderNavbar()

      const button = await screen.findByRole('button', { name: 'Notifications (2 unread)' })
      // Only unread items count — the read one must not inflate the badge.
      expect(within(button).getByText('2')).toBeInTheDocument()
    })

    it('shows no badge when everything is read', async () => {
      serveNotifications([notification({ id: 'a', read: true })])
      loginAs()
      renderNavbar()

      await waitFor(() =>
        expect(screen.getByRole('button', { name: 'Notifications' })).toBeInTheDocument(),
      )
      expect(within(bell()).queryByText('0')).not.toBeInTheDocument()
    })

    it('caps the badge at 9+ rather than widening', async () => {
      serveNotifications(
        Array.from({ length: 12 }, (_, i) => notification({ id: `n-${i}`, read: false })),
      )
      loginAs()
      renderNavbar()

      const button = await screen.findByRole('button', { name: 'Notifications (12 unread)' })
      expect(within(button).getByText('9+')).toBeInTheDocument()
    })

    it('keeps the panel closed until the bell is clicked', async () => {
      loginAs()
      renderNavbar()
      await screen.findByRole('button', { name: /1 unread/i })

      expect(screen.queryByRole('menu')).not.toBeInTheDocument()
      expect(bell()).toHaveAttribute('aria-expanded', 'false')
    })

    it('lists the notifications when opened', async () => {
      const user = userEvent.setup()
      loginAs()
      renderNavbar()
      await screen.findByRole('button', { name: /1 unread/i })

      await user.click(bell())

      const panel = screen.getByRole('menu', { name: 'Notifications' })
      expect(within(panel).getByText(UNREAD.title)).toBeInTheDocument()
      expect(within(panel).getByText(UNREAD.body)).toBeInTheDocument()
      expect(within(panel).getByText(READ.title)).toBeInTheDocument()
      expect(bell()).toHaveAttribute('aria-expanded', 'true')
    })

    it('shows skeleton rows while the inbox is loading', async () => {
      server.use(
        http.get('*/api/notifications', async () => {
          await delay(50)
          return HttpResponse.json([])
        }),
      )
      const user = userEvent.setup()
      loginAs()
      renderNavbar()

      await user.click(bell())

      expect(screen.getByRole('status', { name: 'Loading notifications' })).toBeInTheDocument()
      await waitFor(() => expect(screen.getByText('No notifications')).toBeInTheDocument())
    })

    it('shows a compact empty message', async () => {
      serveNotifications([])
      const user = userEvent.setup()
      loginAs()
      renderNavbar()
      await waitFor(() =>
        expect(screen.getByRole('button', { name: 'Notifications' })).toBeInTheDocument(),
      )

      await user.click(bell())

      expect(screen.getByText('No notifications')).toBeInTheDocument()
    })

    it('shows a compact error message when the inbox fails', async () => {
      server.use(http.get('*/api/notifications', () => new HttpResponse(null, { status: 500 })))
      const user = userEvent.setup()
      loginAs()
      renderNavbar()

      await user.click(bell())

      expect(await screen.findByRole('alert')).toHaveTextContent(
        'Something went wrong. Please try again.',
      )
    })
  })

  describe('marking read', () => {
    it('calls PUT /notifications/{id}/read for the clicked row and clears its badge', async () => {
      const markedIds: string[] = []
      /**
       * Stateful on purpose. `useMarkNotificationRead` invalidates on settle, so
       * a stateless GET would re-serve the item as unread and the badge would
       * flicker back — which would make this assert the mock's amnesia rather
       * than the mutation's effect.
       */
      const inbox: NotificationDto[] = MOCK_NOTIFICATION_DTOS.map((n) => ({ ...n }))
      server.use(
        http.get('*/api/notifications', () => HttpResponse.json(inbox)),
        http.put('*/api/notifications/:id/read', ({ params }) => {
          const id = String(params.id)
          markedIds.push(id)
          const target = inbox.find((n) => n.id === id)!
          target.read = true
          return HttpResponse.json(target)
        }),
      )
      const user = userEvent.setup()
      loginAs()
      renderNavbar()
      await screen.findByRole('button', { name: /1 unread/i })
      await user.click(bell())

      await user.click(
        screen.getByRole('button', { name: `Mark "${UNREAD.title}" as read` }),
      )

      await waitFor(() => expect(markedIds).toEqual([UNREAD.id]))
      await waitFor(() =>
        expect(screen.getByRole('button', { name: 'Notifications' })).toBeInTheDocument(),
      )
      // The row survives; only its unread affordance goes away.
      expect(screen.getByText(UNREAD.title)).toBeInTheDocument()
      expect(
        screen.queryByRole('button', { name: `Mark "${UNREAD.title}" as read` }),
      ).not.toBeInTheDocument()
    })

    /** An already-read row has nothing to do, so it isn't a control. */
    it('offers no mark-read affordance on already-read rows', async () => {
      const user = userEvent.setup()
      loginAs()
      renderNavbar()
      await screen.findByRole('button', { name: /1 unread/i })
      await user.click(bell())

      expect(
        screen.queryByRole('button', { name: `Mark "${READ.title}" as read` }),
      ).not.toBeInTheDocument()
      expect(screen.getByText(READ.title)).toBeInTheDocument()
    })
  })

  describe('dismissing the dropdown', () => {
    it('closes on Escape', async () => {
      const user = userEvent.setup()
      loginAs()
      renderNavbar()
      await screen.findByRole('button', { name: /1 unread/i })
      await user.click(bell())
      expect(screen.getByRole('menu')).toBeInTheDocument()

      await user.keyboard('{Escape}')

      expect(screen.queryByRole('menu')).not.toBeInTheDocument()
      expect(bell()).toHaveAttribute('aria-expanded', 'false')
    })

    it('closes on an outside click', async () => {
      const user = userEvent.setup()
      loginAs()
      renderNavbar()
      await screen.findByRole('button', { name: /1 unread/i })
      await user.click(bell())
      expect(screen.getByRole('menu')).toBeInTheDocument()

      await user.click(screen.getByRole('button', { name: 'somewhere else' }))

      expect(screen.queryByRole('menu')).not.toBeInTheDocument()
    })

    it('stays open when the click lands inside the panel', async () => {
      const user = userEvent.setup()
      loginAs()
      renderNavbar()
      await screen.findByRole('button', { name: /1 unread/i })
      await user.click(bell())

      await user.click(screen.getByText('Notifications', { selector: 'span' }))

      expect(screen.getByRole('menu')).toBeInTheDocument()
    })

    /**
     * The reason the outside-click listener is on `pointerdown` and skips the
     * container: on `click`, the document handler would close the panel and the
     * bell's own handler would immediately reopen it.
     */
    it('closes when the bell itself is clicked again', async () => {
      const user = userEvent.setup()
      loginAs()
      renderNavbar()
      await screen.findByRole('button', { name: /1 unread/i })
      await user.click(bell())
      expect(screen.getByRole('menu')).toBeInTheDocument()

      await user.click(bell())

      expect(screen.queryByRole('menu')).not.toBeInTheDocument()
    })
  })
})
