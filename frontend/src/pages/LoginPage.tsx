import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Users, Mail, Lock } from 'lucide-react'
import { Button } from '@/shared/components/ui/Button'
import { Input } from '@/shared/components/ui/Input'
import { useAuthStore } from '@/stores'

export function LoginPage() {
  const navigate = useNavigate()
  const login = useAuthStore((s) => s.login)

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')

    if (!email || !password) {
      setError('Please fill in all fields.')
      return
    }

    setLoading(true)
    // Simulate network delay
    await new Promise((r) => setTimeout(r, 800))
    setLoading(false)

    login({
      userId: '1',
      role: 'user',
      accessToken: 'mock-access-token',
      refreshToken: 'mock-refresh-token',
    })

    navigate('/explore')
  }

  const handleAdminLogin = async () => {
    setLoading(true)
    await new Promise((r) => setTimeout(r, 600))
    setLoading(false)
    login({
      userId: 'admin-1',
      role: 'admin',
      accessToken: 'mock-admin-token',
      refreshToken: 'mock-admin-refresh',
    })
    navigate('/admin')
  }

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
            {error && (
              <div className="bg-danger-100 border border-danger-600/20 rounded-xl px-4 py-3 text-sm text-danger-600 font-medium">
                {error}
              </div>
            )}

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
              loading={loading}
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

        {/* Admin login */}
        <div className="mt-4 text-center">
          <button
            onClick={handleAdminLogin}
            className="text-xs text-ink-400 hover:text-ink-600 transition-colors underline underline-offset-2"
          >
            Admin login (demo)
          </button>
        </div>
      </div>
    </div>
  )
}
