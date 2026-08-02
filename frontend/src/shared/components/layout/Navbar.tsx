import { Link, useNavigate } from 'react-router-dom'
import { Users } from 'lucide-react'
import { Button } from '@/shared/components/ui/Button'
import { NotificationDropdown } from '@/features/notifications/components/NotificationDropdown'
import { useAuthStore } from '@/stores'

const NAV_LINKS = [
  { label: 'Explore Advisors', to: '/explore' },
  { label: 'Become an Advisor', to: '/onboarding' },
]

export function Navbar() {
  const { isAuthenticated, role, logout } = useAuthStore()
  const navigate = useNavigate()

  const dashboardPath = role === 'admin' ? '/admin' : role === 'advisor' ? '/dashboard' : '/explore'

  return (
    <header className="fixed top-0 w-full z-50 bg-white border-b border-ink-200">
      <div className="max-w-7xl mx-auto px-4 flex items-center justify-between h-16">
        {/* Logo */}
        <Link to="/" className="flex items-center gap-2 group">
          <div className="w-8 h-8 bg-oxblood-600 rounded-lg flex items-center justify-center group-hover:bg-oxblood-700 transition-colors">
            <Users className="w-5 h-5 text-white" />
          </div>
          <span className="font-heading font-medium text-ink-900 text-lg">AdvisorConnect</span>
        </Link>

        {/* Centre links */}
        <nav className="hidden md:flex items-center gap-6 text-sm font-medium text-ink-600" aria-label="Main navigation">
          {NAV_LINKS.map((l) => (
            <Link key={l.to} to={l.to} className="hover:text-oxblood-700 transition-colors">
              {l.label}
            </Link>
          ))}
          {/* Sessions belong to users and advisors alike, so this sits with the
              public links rather than behind a role check — only behind auth. */}
          {isAuthenticated && (
            <Link to="/bookings" className="hover:text-oxblood-700 transition-colors">
              My Bookings
            </Link>
          )}
        </nav>

        {/* Right CTA */}
        <div className="flex items-center gap-3">
          {isAuthenticated ? (
            <>
              <NotificationDropdown />
              <Button variant="ghost" size="sm" onClick={() => navigate(dashboardPath)}>
                {role === 'admin' ? 'Admin Panel' : 'Dashboard'}
              </Button>
              <Button variant="outline" size="sm" onClick={() => { logout(); navigate('/') }}>
                Log out
              </Button>
            </>
          ) : (
            <>
              <Link to="/login">
                <Button variant="ghost" size="sm">Log in</Button>
              </Link>
              <Link to="/register">
                <Button variant="primary" size="sm">Sign up free</Button>
              </Link>
            </>
          )}
        </div>
      </div>
    </header>
  )
}
