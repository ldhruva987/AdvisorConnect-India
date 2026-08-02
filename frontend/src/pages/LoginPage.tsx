import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { Users, Mail, Lock } from 'lucide-react'
import { ROLE_HOME } from '@/app/roleHome'
import { useLogin } from '@/features/auth/hooks/useLogin'
import { getErrorMessage } from '@/lib/getErrorMessage'
import { Button } from '@/shared/components/ui/Button'
import { ErrorBanner } from '@/shared/components/ui/ErrorBanner'
import { Input } from '@/shared/components/ui/Input'
import { useAuthStore } from '@/stores'

export function LoginPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const login = useAuthStore((s) => s.login)

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  /** Client-side validation only. Server failures come off the mutation. */
  const [formError, setFormError] = useState('')

  const { mutate, isPending, error, reset } = useLogin()

  // Set by ProtectedRoute when it bounced an unauthenticated visitor here.
  const from = (location.state as { from?: { pathname: string } } | null)?.from?.pathname

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    setFormError('')
    // Clear a previous failure so a resubmit doesn't show a stale banner.
    reset()

    if (!email || !password) {
      setFormError('Please fill in all fields.')
      return
    }

    mutate(
      { email, password },
      {
        onSuccess: (tokens) => {
          login({
            userId: tokens.userId,
            role: tokens.role,
            accessToken: tokens.accessToken,
            refreshToken: tokens.refreshToken,
          })
          // Back to whatever they were denied, else their role's home. Role
          // homes matter now that the "Admin login (demo)" button is gone:
          // admins sign in through this same form and must still land on
          // /admin. `replace` keeps /login out of the back-button history.
          navigate(from ?? ROLE_HOME[tokens.role], { replace: true })
        },
      },
    )
  }

  const errorMessage = formError || (error ? getErrorMessage(error) : '')

  return (
    <div className="min-h-screen bg-ink-50 flex items-center justify-center px-4 pt-16">
      <div className="w-full max-w-md">
        {/* Logo */}
        <div className="text-center mb-8">
          <div className="w-14 h-14 bg-oxblood-600 rounded-xl flex items-center justify-center mx-auto mb-4">
            <Users className="w-7 h-7 text-white" />
          </div>
          <h1 className="font-heading font-medium text-3xl text-ink-900">Welcome back</h1>
          <p className="text-ink-500 mt-2">Sign in to your AdvisorConnect account</p>
        </div>

        {/* Card */}
        <div className="bg-white rounded-xl border border-ink-200 p-8">
          <form onSubmit={handleSubmit} className="space-y-5">
            {errorMessage && <ErrorBanner message={errorMessage} />}

            <Input
              label="Email address"
              type="email"
              placeholder="you@example.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              leftIcon={<Mail className="w-4 h-4" />}
              required
              autoComplete="email"
            />

            <div>
              <Input
                label="Password"
                type="password"
                placeholder="Enter your password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                leftIcon={<Lock className="w-4 h-4" />}
                required
                autoComplete="current-password"
              />
              <div className="flex justify-end mt-1.5">
                <a href="#" className="text-xs text-oxblood-700 hover:text-oxblood-600 font-medium transition-colors">
                  Forgot password?
                </a>
              </div>
            </div>

            <Button
              type="submit"
              variant="primary"
              size="lg"
              fullWidth
              loading={isPending}
            >
              Sign In
            </Button>
          </form>

          <p className="text-center text-sm text-ink-500 mt-5">
            Don't have an account?{' '}
            <Link to="/register" className="text-oxblood-700 font-semibold hover:text-oxblood-600 transition-colors">
              Sign up free
            </Link>
          </p>
        </div>
      </div>
    </div>
  )
}
