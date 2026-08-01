import { Link } from 'react-router-dom'
import { CheckCircle, Star } from 'lucide-react'
import { Button } from '@/shared/components/ui/Button'
import { Footer } from '@/shared/components/layout/Footer'
import { Navbar } from '@/shared/components/layout/Navbar'
import { AdvisorCard } from '@/features/explore/components/AdvisorCard'
import type { AdvisorPublic } from '@/types'

const MOCK_ADVISORS: AdvisorPublic[] = [
  {
    id: '1',
    username: '@MindfulRohan',
    title: 'Licensed Clinical Psychologist',
    bio: '8 years helping people with anxiety, depression, and relationship challenges using CBT and mindfulness techniques tailored to each person.',
    tags: ['Mental Health', 'CBT', 'Anxiety'],
    rating: 4.9,
    reviewCount: 342,
    chatCount: 1247,
    responseTimeMinutes: 5,
    experienceYears: 8,
    isOnline: true,
    isVerified: true,
    color: '#005e8f',
    sectors: ['Mental Health', 'Relationships'],
    languages: ['English'],
  },
  {
    id: '2',
    username: '@SarahCareerPro',
    title: 'ICF-Certified Career Coach',
    bio: 'Specialized in career transitions, salary negotiation, and executive presence. Helped 500+ professionals level up their careers.',
    tags: ['Career', 'Leadership', 'Negotiation'],
    rating: 4.8,
    reviewCount: 218,
    chatCount: 892,
    responseTimeMinutes: 10,
    experienceYears: 10,
    isOnline: true,
    isVerified: true,
    color: '#516000',
    sectors: ['Career'],
    languages: ['English', 'Spanish'],
  },
  {
    id: '3',
    username: '@FinanceWithTed',
    title: 'CFP & Investment Advisor',
    bio: 'Certified Financial Planner with 12 years in wealth management and personal finance for young professionals and families.',
    tags: ['Finance', 'Investment', 'Tax'],
    rating: 4.9,
    reviewCount: 156,
    chatCount: 634,
    responseTimeMinutes: 20,
    experienceYears: 12,
    isOnline: false,
    isVerified: true,
    color: '#4d4f94',
    sectors: ['Finance'],
    languages: ['English'],
  },
  {
    id: '4',
    username: '@EmpatheticJana',
    title: 'Relationship Counsellor',
    bio: 'Couples and family therapist specializing in communication, trust rebuilding, and healthy attachment styles.',
    tags: ['Relationships', 'Family', 'Communication'],
    rating: 5.0,
    reviewCount: 89,
    chatCount: 321,
    responseTimeMinutes: 8,
    experienceYears: 6,
    isOnline: true,
    isVerified: true,
    color: '#843b61',
    sectors: ['Relationships'],
    languages: ['English', 'French'],
  },
]

const TESTIMONIALS = [
  {
    id: '1',
    name: '@AnxiousAndrew',
    color: '#005e8f',
    rating: 5,
    text: 'I was skeptical about getting advice online, but @MindfulRohan changed my perspective completely. The free chat was incredibly helpful and I felt genuinely understood for the first time.',
    role: 'Marketing Manager',
  },
  {
    id: '2',
    name: '@CareerJumperKate',
    color: '#516000',
    rating: 5,
    text: 'Booked a 30-min video session with @SarahCareerPro before a big salary negotiation. She gave me a script and confidence I never had. Got a 22% raise!',
    role: 'Software Engineer',
  },
  {
    id: '3',
    name: '@FirstTimeBuyerJoe',
    color: '#4d4f94',
    rating: 5,
    text: 'The finance advisor walked me through my first mortgage step by step. No jargon, no pressure. I finally understood what I was signing. Completely free too!',
    role: 'Teacher',
  },
]

const CATEGORIES_DATA = [
  { emoji: '💼', label: 'Career', count: 68, sector: 'Career' },
  { emoji: '💛', label: 'Relationships', count: 54, sector: 'Relationships' },
  { emoji: '📈', label: 'Finance', count: 47, sector: 'Finance' },
  { emoji: '🧠', label: 'Mental Health', count: 79, sector: 'Mental+Health' },
]

const HOW_IT_WORKS = [
  {
    step: '01',
    emoji: '🔍',
    emojiColor: 'bg-oxblood-50',
    title: 'Browse Verified Advisors',
    description: 'Search by specialty, read reviews, and find the right expert for your situation — all verified and background-checked.',
  },
  {
    step: '02',
    emoji: '💬',
    emojiColor: 'bg-pine-100',
    title: 'Chat for Free',
    description: 'Start a conversation instantly at no cost. Ask questions, share your situation, and get real professional guidance.',
  },
  {
    step: '03',
    emoji: '📹',
    emojiColor: 'bg-ink-100',
    title: 'Book a Video Session',
    description: 'Ready for a deeper consultation? Book a 30 or 60-minute video call starting at just $39. Cancel anytime.',
  },
]

const AVATAR_COLORS = ['#005e8f', '#516000', '#4d4f94', '#843b61', '#784f00']

export function LandingPage() {
  return (
    <div className="min-h-screen">
      <Navbar />

      {/* ── 1. HERO ─────────────────────────────────────────────────── */}
      <section className="pt-16 bg-ink-900 text-white relative overflow-hidden">
        {/* Radial gradient overlay */}
        <div
          className="absolute inset-0 opacity-20 pointer-events-none"
          style={{ background: 'radial-gradient(ellipse at 60% 40%, #8b173d 0%, transparent 70%)' }}
        />
        <div className="relative max-w-7xl mx-auto px-4 py-20 md:py-28 text-center">
          {/* Live pill */}
          <div className="inline-flex items-center gap-2 bg-white/10 border border-white/20 rounded-full px-4 py-2 text-sm font-medium mb-8">
            <span className="w-2 h-2 rounded-full bg-pine-600 animate-pulse" />
            1,200+ verified advisors online now
          </div>

          {/* Headline */}
          <h1 className="font-heading font-medium text-4xl md:text-6xl leading-tight mb-6">
            Real Experts.
            <br />
            <span className="text-oxblood-500">Free Advice.</span>
            <br />
            Always Private.
          </h1>

          <p className="text-lg text-white/80 max-w-2xl mx-auto mb-10 leading-relaxed">
            Connect with verified career coaches, therapists, financial advisors, and relationship counsellors.
            Get real help — free, private, and on your terms.
          </p>

          {/* CTA buttons */}
          <div className="flex flex-col sm:flex-row gap-4 justify-center mb-12">
            <Link to="/explore">
              <Button variant="ghost" size="lg" className="bg-white text-oxblood-700 hover:bg-oxblood-50">Start Free Chat</Button>
            </Link>
            <Link to="/onboarding">
              <Button
                variant="outline"
                size="lg"
                className="border-white/30 text-white hover:bg-white/10 hover:border-white hover:text-white"
              >
                Become an Advisor
              </Button>
            </Link>
          </div>

          {/* Social proof */}
          <div className="flex items-center justify-center gap-3">
            <div className="flex -space-x-2">
              {AVATAR_COLORS.map((color, i) => (
                <div
                  key={i}
                  className="w-9 h-9 rounded-full border-2 border-ink-900 flex items-center justify-center text-white text-xs font-bold"
                  style={{ background: color }}
                >
                  {String.fromCharCode(65 + i)}
                </div>
              ))}
            </div>
            <div className="text-left">
              <div className="flex items-center gap-1">
                <Star className="w-4 h-4 text-warn-500 fill-warn-500" />
                <span className="font-semibold text-white">4.9★</span>
              </div>
              <p className="text-xs text-white/70">from 28,000+ sessions</p>
            </div>
          </div>
        </div>
      </section>

      {/* ── 2. CATEGORY PILLS ───────────────────────────────────────── */}
      <section className="bg-white border-b border-ink-200 py-5">
        <div className="max-w-7xl mx-auto px-4">
          <div className="flex flex-wrap gap-3 justify-center">
            {CATEGORIES_DATA.map((cat) => (
              <Link
                key={cat.label}
                to={`/explore?sector=${cat.sector}`}
                className="flex items-center gap-2 px-5 py-2.5 rounded-full border border-ink-200 hover:border-oxblood-700 hover:bg-oxblood-50 transition-all text-sm font-medium text-ink-700 hover:text-oxblood-700"
              >
                <span>{cat.emoji}</span>
                <span>{cat.label}</span>
                <span className="bg-ink-100 text-ink-500 text-xs font-semibold px-2 py-0.5 rounded-full">
                  {cat.count}
                </span>
              </Link>
            ))}
          </div>
        </div>
      </section>

      {/* ── 3. HOW IT WORKS ─────────────────────────────────────────── */}
      <section className="py-20 bg-ink-50">
        <div className="max-w-7xl mx-auto px-4 text-center">
          <h2 className="font-heading font-medium text-3xl text-ink-900 mb-12">How It Works</h2>
          <div className="grid md:grid-cols-3 gap-8">
            {HOW_IT_WORKS.map((item) => (
              <div key={item.step} className="bg-white rounded-xl p-8 border border-ink-200 relative overflow-hidden text-left">
                {/* Step watermark */}
                <div className="absolute top-4 right-6 font-heading font-medium text-7xl text-ink-100 leading-none select-none">
                  {item.step}
                </div>
                <div className={`w-12 h-12 rounded-xl ${item.emojiColor} flex items-center justify-center text-2xl mb-5`}>
                  {item.emoji}
                </div>
                <h3 className="font-heading font-medium text-lg text-ink-900 mb-2">{item.title}</h3>
                <p className="text-ink-500 text-sm leading-relaxed">{item.description}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ── 4. FEATURED ADVISORS ────────────────────────────────────── */}
      <section className="py-20 bg-white">
        <div className="max-w-7xl mx-auto px-4">
          <div className="flex items-center justify-between mb-8">
            <h2 className="font-heading font-medium text-3xl text-ink-900">Featured Advisors</h2>
            <Link to="/explore" className="text-oxblood-700 font-semibold text-sm hover:text-oxblood-600 transition-colors">
              View all →
            </Link>
          </div>
          <div className="grid sm:grid-cols-2 lg:grid-cols-4 gap-5">
            {MOCK_ADVISORS.map((advisor) => (
              <AdvisorCard key={advisor.id} advisor={advisor} />
            ))}
          </div>
        </div>
      </section>

      {/* ── 5. PRICING ──────────────────────────────────────────────── */}
      <section id="pricing" className="py-20 bg-ink-50">
        <div className="max-w-4xl mx-auto px-4 text-center">
          <h2 className="font-heading font-medium text-3xl text-ink-900 mb-12">Simple, Honest Pricing</h2>
          <div className="grid md:grid-cols-2 gap-6">
            {/* Free card */}
            <div className="bg-white rounded-xl border border-ink-200 p-8 text-left">
              <div className="mb-6">
                <p className="text-sm font-semibold text-ink-600 uppercase tracking-wider mb-2">Free Chat</p>
                <div className="flex items-end gap-2">
                  <span className="font-heading font-medium text-5xl text-ink-900">$0</span>
                  <span className="text-ink-400 mb-2">forever</span>
                </div>
              </div>
              <ul className="space-y-3 mb-8">
                {[
                  'Unlimited text chat with advisors',
                  'Browse all verified profiles',
                  'Read reviews & credentials',
                  'Completely anonymous identity',
                  'No credit card required',
                ].map((item) => (
                  <li key={item} className="flex items-center gap-3 text-sm text-ink-700">
                    <CheckCircle className="w-4 h-4 text-pine-600 flex-shrink-0" />
                    {item}
                  </li>
                ))}
              </ul>
              <Link to="/register">
                <Button variant="outline" fullWidth>Get Started Free</Button>
              </Link>
            </div>

            {/* Paid card */}
            <div className="bg-ink-900 rounded-xl p-8 text-left relative overflow-hidden">
              <div
                className="absolute inset-0 opacity-25 pointer-events-none"
                style={{ background: 'radial-gradient(ellipse at 70% 30%, #8b173d 0%, transparent 60%)' }}
              />
              <div className="relative">
                <div className="flex items-start justify-between mb-6">
                  <div>
                    <p className="text-sm font-semibold text-oxblood-100 uppercase tracking-wider mb-2">Video Session</p>
                    <div className="flex items-end gap-2">
                      <span className="font-heading font-medium text-5xl text-white">$39</span>
                      <span className="text-white/70 mb-2">/ 30 min</span>
                    </div>
                  </div>
                  <span className="bg-oxblood-600 text-white text-xs font-bold px-3 py-1 rounded-full">POPULAR</span>
                </div>
                <ul className="space-y-3 mb-8">
                  {[
                    'Face-to-face video consultation',
                    'Screen sharing & document review',
                    'Session recording (optional)',
                    'Follow-up message included',
                    '60-min sessions available at $69',
                  ].map((item) => (
                    <li key={item} className="flex items-center gap-3 text-sm text-white">
                      <CheckCircle className="w-4 h-4 text-oxblood-100 flex-shrink-0" />
                      {item}
                    </li>
                  ))}
                </ul>
                <Link to="/explore">
                  <Button variant="ghost" fullWidth size="lg" className="bg-white text-oxblood-700 hover:bg-oxblood-50">Book a Session</Button>
                </Link>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* ── 6. TESTIMONIALS ─────────────────────────────────────────── */}
      <section className="py-20 bg-white">
        <div className="max-w-7xl mx-auto px-4 text-center">
          <h2 className="font-heading font-medium text-3xl text-ink-900 mb-12">What People Are Saying</h2>
          <div className="grid md:grid-cols-3 gap-6">
            {TESTIMONIALS.map((t) => (
              <div key={t.id} className="bg-ink-50 rounded-xl p-6 text-left border border-ink-100">
                <div className="flex mb-3">
                  {Array.from({ length: t.rating }).map((_, i) => (
                    <Star key={i} className="w-4 h-4 text-warn-500 fill-warn-500" />
                  ))}
                </div>
                <p className="text-ink-700 text-sm leading-relaxed mb-4">"{t.text}"</p>
                <div className="flex items-center gap-2">
                  <div
                    className="w-8 h-8 rounded-full flex items-center justify-center text-white text-xs font-bold"
                    style={{ background: t.color }}
                  >
                    {t.name.replace('@', '').slice(0, 2).toUpperCase()}
                  </div>
                  <div>
                    <p className="text-xs font-semibold text-ink-800">{t.name}</p>
                    <p className="text-xs text-ink-400">{t.role}</p>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ── 7. CTA BANNER ───────────────────────────────────────────── */}
      <section className="bg-oxblood-700 py-16">
        <div className="max-w-3xl mx-auto px-4 text-center">
          <h2 className="font-heading font-medium text-3xl text-white mb-4">
            Ready to get expert advice — for free?
          </h2>
          <p className="text-oxblood-100 mb-8 text-lg">
            Join 28,000+ people who've already found clarity, direction, and peace of mind through AdvisorConnect.
          </p>
          <Link to="/explore">
            <Button variant="ghost" size="lg" className="bg-white text-oxblood-700 hover:bg-oxblood-50">Start Your Free Chat Now</Button>
          </Link>
        </div>
      </section>

      <Footer />
    </div>
  )
}
