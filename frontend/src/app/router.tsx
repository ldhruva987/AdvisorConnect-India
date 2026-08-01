import { createBrowserRouter } from 'react-router-dom'
import { LandingPage } from '@/pages/LandingPage'
import { LoginPage } from '@/pages/LoginPage'
import { RegisterPage } from '@/pages/RegisterPage'
import { ExplorePage } from '@/pages/ExplorePage'
import { AdvisorProfilePage } from '@/pages/AdvisorProfilePage'
import { ChatPage } from '@/pages/ChatPage'
import { BookingPage } from '@/pages/BookingPage'
import { OnboardingPage } from '@/pages/OnboardingPage'
import { AdvisorDashboardPage } from '@/pages/AdvisorDashboardPage'
import { AdminDashboardPage } from '@/pages/AdminDashboardPage'

export const router = createBrowserRouter([
  { path: '/', element: <LandingPage /> },
  { path: '/login', element: <LoginPage /> },
  { path: '/register', element: <RegisterPage /> },
  { path: '/explore', element: <ExplorePage /> },
  { path: '/advisor/:username', element: <AdvisorProfilePage /> },
  { path: '/chat', element: <ChatPage /> },
  { path: '/chat/:advisorId', element: <ChatPage /> },
  { path: '/book/:advisorId', element: <BookingPage /> },
  { path: '/onboarding', element: <OnboardingPage /> },
  { path: '/dashboard', element: <AdvisorDashboardPage /> },
  { path: '/admin', element: <AdminDashboardPage /> },
])
