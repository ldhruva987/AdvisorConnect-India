import { createBrowserRouter, type RouteObject } from 'react-router-dom'
import { LandingPage } from '@/pages/LandingPage'
import { LoginPage } from '@/pages/LoginPage'
import { RegisterPage } from '@/pages/RegisterPage'
import { ExplorePage } from '@/pages/ExplorePage'
import { AdvisorProfilePage } from '@/pages/AdvisorProfilePage'
import { ChatPage } from '@/pages/ChatPage'
import { BookingPage } from '@/pages/BookingPage'
import { OnboardingPage } from '@/pages/OnboardingPage'
import { MyBookingsPage } from '@/pages/MyBookingsPage'
import { AdvisorDashboardPage } from '@/pages/AdvisorDashboardPage'
import { AdminDashboardPage } from '@/pages/AdminDashboardPage'
import { ProtectedRoute } from './ProtectedRoute'

/**
 * The route table, exported separately from `router` so tests can mount the
 * real thing in a `createMemoryRouter` and assert on guard behaviour
 * end-to-end. Asserting against `createBrowserRouter` would mean driving
 * `window.history` and would not prove these exact routes are the guarded ones.
 */
export const routes: RouteObject[] = [
  { path: '/', element: <LandingPage /> },
  { path: '/login', element: <LoginPage /> },
  { path: '/register', element: <RegisterPage /> },
  { path: '/explore', element: <ExplorePage /> },
  { path: '/advisor/:username', element: <AdvisorProfilePage /> },
  { path: '/chat', element: <ChatPage /> },
  { path: '/chat/:advisorId', element: <ChatPage /> },
  { path: '/book/:advisorId', element: <BookingPage /> },
  { path: '/onboarding', element: <OnboardingPage /> },
  {
    // No `allowedRoles`: advisors have sessions too, and so do admins who book
    // one. `ProtectedRoute` with the prop omitted requires only that someone is
    // signed in, which is exactly the bar here.
    path: '/bookings',
    element: (
      <ProtectedRoute>
        <MyBookingsPage />
      </ProtectedRoute>
    ),
  },
  {
    path: '/dashboard',
    element: (
      <ProtectedRoute allowedRoles={['advisor']}>
        <AdvisorDashboardPage />
      </ProtectedRoute>
    ),
  },
  {
    path: '/admin',
    element: (
      <ProtectedRoute allowedRoles={['admin']}>
        <AdminDashboardPage />
      </ProtectedRoute>
    ),
  },
]

export const router = createBrowserRouter(routes)
