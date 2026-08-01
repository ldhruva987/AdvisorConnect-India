import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Users, Mail, Lock } from 'lucide-react'
import { Button } from '@/shared/components/ui/Button'
import { Input } from '@/shared/components/ui/Input'
import { useAuthStore } from '@/stores'

type UserType = 'seeker' | 'advisor'

export function RegisterPage() {
  const navigate = useNavigate()
  const login = useAuthStore((s) => s.login)

  const [userType, setUserType] = useState<UserType>('seeker')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')

    if (userType === 'advisor') {
      navigate('/onboarding')
      return
    }

    if (!email || !password) {
      setError('Please fill in all fields.')
      return
    }
    if (password.length < 8) {
      setError('Password must be at least 8 characters.')
      return
    }

    setLoading(true)
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

  return (
    <div className="min-h-screen bg-ink-50 flex items-center justify-center px-4 pt-16">
      <div className="w-full max-w-md">
        {/* Logo */}
        <div className="text-center mb-8">
          <div className="w-14 h-14 bg-oxblood-600 rounded-xl flex items-center justify-center mx-auto mb-4">
            <Users className="w-7 h-7 text-white" />
          </div>
          <h1 className="font-heading font-medium text-3xl text-ink-900">Create your account</h1>
          <p className="text-ink-500 mt-2">Join thousands getting free expert advice</p>
        </div>

        {/* Card */}
        <div className="bg-white rounded-xl border border-ink-200 p-8">
          {/* User type selector */}
          <div className="grid grid-cols-2 gap-3 mb-6">
            <button
              type="button"
              onClick={() => setUserType('seeker')}
              className={`flex flex-col items-center gap-2 p-4 rounded-xl border-2 transition-all ${
                userType === 'seeker'
                  ? 'border-oxblood-700 bg-oxblood-50'
                  : 'border-ink-200 hover:border-ink-300'
              }`}
            >
              <span className="text-2xl">👤</span>
              <span className="text-sm font-semibold text-ink-800">Seeking Advice</span>
              <span className="text-xs text-ink-400 text-center">Find experts and get help</span>
            </button>
            <button
              type="button"
              onClick={() => setUserType('advisor')}
              className={`flex flex-col items-center gap-2 p-4 rounded-xl border-2 transition-all ${
                userType === 'advisor'
                  ? 'border-oxblood-700 bg-oxblood-50'
                  : 'border-ink-200 hover:border-ink-300'
              }`}
            >
              <span className="text-2xl">🎓</span>
              <span className="text-sm font-semibold text-ink-800">I'm an Advisor</span>
              <span className="text-xs text-ink-400 text-center">Share your expertise</span>
            </button>
          </div>

          {userType === 'advisor' ? (
            <div className="text-center py-4">
              <div className="bg-oxblood-50 rounded-xl p-4 mb-5">
                <p className="text-sm text-ink-700 font-medium mb-1">Advisor Application Required</p>
                <p className="text-xs text-ink-500">
                  Advisors go through a verification process to ensure quality and trust for our users.
                  Click below to start your application.
                </p>
              </div>
              <Button variant="primary" size="lg" fullWidth onClick={() => navigate('/onboarding')}>
                Start Advisor Application →
              </Button>
            </div>
          ) : (
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

              <Input
                label="Password"
                type="password"
                placeholder="Create a strong password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                leftIcon={<Lock className="w-4 h-4" />}
                required
                helper="Minimum 8 characters"
                autoComplete="new-password"
              />

              <Button
                type="submit"
                variant="primary"
                size="lg"
                fullWidth
                loading={loading}
              >
                Create Free Account
              </Button>

              <p className="text-xs text-ink-400 text-center leading-relaxed">
                By creating an account, you agree to our{' '}
                <a href="/terms" className="text-oxblood-700 hover:underline">Terms of Service</a>
                {' '}and{' '}
                <a href="/privacy" className="text-oxblood-700 hover:underline">Privacy Policy</a>.
                Your real identity is always kept private.
              </p>
            </form>
          )}

          <p className="text-center text-sm text-ink-500 mt-5">
            Already have an account?{' '}
            <Link to="/login" className="text-oxblood-700 font-semibold hover:text-oxblood-600 transition-colors">
              Sign in
            </Link>
          </p>
        </div>
      </div>
    </div>
  )
}
