import { useNavigate } from 'react-router-dom'
import { ArrowLeft, CheckCircle, Star, MessageCircle, Video, Clock, Award } from 'lucide-react'
import { Badge } from '@/shared/components/ui/Badge'
import { Button } from '@/shared/components/ui/Button'
import { Navbar } from '@/shared/components/layout/Navbar'
import type { AdvisorReview } from '@/types'

const MOCK_ADVISOR = {
  id: '1',
  username: '@MindfulRohan',
  title: 'Licensed Clinical Psychologist',
  bio: '8 years helping people with anxiety, depression, and relationship challenges using CBT and mindfulness techniques tailored to each person. I believe in a collaborative, non-judgmental approach where you lead the conversation and I provide professional guidance.\n\nMy sessions focus on practical, evidence-based strategies you can use immediately. Whether you\'re dealing with work stress, relationship difficulties, or just need someone to talk to, I\'m here to help.',
  tags: ['Mental Health', 'CBT', 'Anxiety', 'Depression', 'Mindfulness', 'Relationships', 'Stress'],
  rating: 4.9,
  reviewCount: 342,
  chatCount: 1247,
  responseTimeMinutes: 5,
  experienceYears: 8,
  isOnline: true,
  isVerified: true,
  color: '#8a3f24',
  sectors: ['Mental Health', 'Relationships'],
  languages: ['English'],
}

const ADVISOR_REVIEWS: AdvisorReview[] = [
  {
    id: '1',
    reviewerUsername: '@AnxiousAnna',
    reviewerColor: '#784f00',
    rating: 5,
    text: 'Rohan was incredibly patient and understanding. He helped me identify the root cause of my anxiety and gave me practical CBT techniques I use every day. The free chat was genuinely helpful — I felt heard, not judged.',
    createdAt: 'Mar 10, 2026',
  },
  {
    id: '2',
    reviewerUsername: '@OverwhelmedOliver',
    reviewerColor: '#516000',
    rating: 5,
    text: 'I was skeptical about online therapy but this changed my mind. Booked a video session after our chat and it was worth every penny. Professional, empathetic, and actually helpful.',
    createdAt: 'Mar 5, 2026',
  },
  {
    id: '3',
    reviewerUsername: '@StressedSophia',
    reviewerColor: '#006970',
    rating: 5,
    text: 'Best $39 I\'ve ever spent. The session helped me work through a really tough period at work. Rohan gave me a clear action plan and followed up with resources. Highly recommend.',
    createdAt: 'Feb 28, 2026',
  },
]

export function AdvisorProfilePage() {
  const navigate = useNavigate()

  const stats = [
    { icon: Star, label: 'Rating', value: `${MOCK_ADVISOR.rating}★`, color: 'text-warn-600' },
    { icon: MessageCircle, label: 'Chats', value: '1.2k', color: 'text-oxblood-700' },
    { icon: Award, label: 'Reviews', value: String(MOCK_ADVISOR.reviewCount), color: 'text-pine-600' },
    { icon: Clock, label: 'Response', value: '<5m', color: 'text-ink-700' },
    { icon: Award, label: 'Experience', value: `${MOCK_ADVISOR.experienceYears}yr`, color: 'text-ink-600' },
  ]

  return (
    <div className="min-h-screen bg-ink-50">
      <Navbar />
      <div className="pt-16 max-w-4xl mx-auto px-4 py-8">
        {/* Back */}
        <button
          onClick={() => navigate(-1)}
          className="flex items-center gap-2 text-ink-500 hover:text-ink-900 transition-colors text-sm font-medium mb-6"
        >
          <ArrowLeft className="w-4 h-4" />
          Back
        </button>

        {/* Main card */}
        <div className="bg-white rounded-xl border border-ink-200 overflow-hidden mb-6">
          {/* Cover */}
          <div className="h-32 bg-gradient-to-r from-ink-900 to-oxblood-700" />

          {/* Profile header */}
          <div className="px-6 pb-6">
            <div className="flex items-end justify-between -mt-10 mb-4">
              <div className="w-20 h-20 rounded-xl bg-oxblood-600 border-4 border-white flex items-center justify-center text-white font-medium text-2xl font-heading">
                MR
              </div>
              {MOCK_ADVISOR.isOnline && (
                <div className="flex items-center gap-1.5 bg-pine-100 text-pine-600 text-xs font-semibold px-3 py-1.5 rounded-full">
                  <span className="w-1.5 h-1.5 rounded-full bg-pine-600" />
                  Online now
                </div>
              )}
            </div>

            {/* Name + verified */}
            <div className="flex items-center gap-2 mb-1">
              <h1 className="font-heading font-medium text-2xl text-ink-900">{MOCK_ADVISOR.username}</h1>
              {MOCK_ADVISOR.isVerified && (
                <CheckCircle className="w-5 h-5 text-pine-600" aria-label="Verified" />
              )}
            </div>
            <p className="text-ink-500 mb-5">{MOCK_ADVISOR.title}</p>

            {/* Stats row */}
            <div className="flex flex-wrap items-center gap-0 mb-6 bg-ink-50 rounded-xl overflow-hidden border border-ink-100">
              {stats.map((stat, i) => (
                <div
                  key={stat.label}
                  className={`flex-1 min-w-[80px] text-center py-3 px-2 ${i < stats.length - 1 ? 'border-r border-ink-100' : ''}`}
                >
                  <p className={`font-heading font-medium text-lg ${stat.color}`}>{stat.value}</p>
                  <p className="text-xs text-ink-400 mt-0.5">{stat.label}</p>
                </div>
              ))}
            </div>

            {/* Tags */}
            <div className="flex flex-wrap gap-2 mb-6">
              {MOCK_ADVISOR.tags.map((tag) => (
                <Badge key={tag} variant="brand">{tag}</Badge>
              ))}
            </div>

            {/* About */}
            <div className="mb-6">
              <h3 className="font-heading font-semibold text-ink-900 mb-3">About</h3>
              {MOCK_ADVISOR.bio.split('\n\n').map((para, i) => (
                <p key={i} className="text-ink-600 text-sm leading-relaxed mb-2">{para}</p>
              ))}
            </div>

            {/* Languages */}
            <div className="mb-6">
              <h3 className="font-heading font-semibold text-ink-900 mb-2">Languages</h3>
              <div className="flex gap-2">
                {MOCK_ADVISOR.languages.map((lang) => (
                  <Badge key={lang} variant="default">{lang}</Badge>
                ))}
              </div>
            </div>

            {/* Credentials */}
            <div className="mb-6">
              <h3 className="font-heading font-semibold text-ink-900 mb-3">Credentials</h3>
              <div className="space-y-3">
                <div className="flex items-start gap-3 p-3 bg-pine-100 rounded-xl">
                  <div className="w-8 h-8 rounded-lg bg-white flex items-center justify-center flex-shrink-0 mt-0.5">
                    <CheckCircle className="w-4 h-4 text-pine-600" />
                  </div>
                  <div>
                    <p className="text-sm font-semibold text-ink-900">Ph.D. in Clinical Psychology</p>
                    <p className="text-xs text-ink-500 select-none blur-sm">University of ████████ · 2016</p>
                  </div>
                </div>
                <div className="flex items-start gap-3 p-3 bg-pine-100 rounded-xl">
                  <div className="w-8 h-8 rounded-lg bg-white flex items-center justify-center flex-shrink-0 mt-0.5">
                    <CheckCircle className="w-4 h-4 text-pine-600" />
                  </div>
                  <div>
                    <p className="text-sm font-semibold text-ink-900">Licensed Clinical Psychologist</p>
                    <p className="text-xs text-ink-500 select-none blur-sm">License #████████ · Verified 2026</p>
                  </div>
                </div>
              </div>
            </div>

            {/* Reviews */}
            <div>
              <div className="flex items-center justify-between mb-4">
                <h3 className="font-heading font-semibold text-ink-900">
                  Reviews <span className="text-ink-400 font-normal">({MOCK_ADVISOR.reviewCount})</span>
                </h3>
                <div className="flex items-center gap-1">
                  <Star className="w-4 h-4 text-warn-500 fill-warn-500" />
                  <span className="font-semibold text-ink-900">{MOCK_ADVISOR.rating}</span>
                </div>
              </div>
              <div className="space-y-4">
                {ADVISOR_REVIEWS.map((review) => (
                  <div key={review.id} className="border border-ink-100 rounded-xl p-4">
                    <div className="flex items-center justify-between mb-2">
                      <div className="flex items-center gap-2">
                        <div
                          className="w-7 h-7 rounded-full flex items-center justify-center text-white text-xs font-bold"
                          style={{ background: review.reviewerColor }}
                        >
                          {review.reviewerUsername.replace('@', '').slice(0, 2).toUpperCase()}
                        </div>
                        <span className="text-sm font-semibold text-ink-800">{review.reviewerUsername}</span>
                      </div>
                      <div className="flex items-center gap-1">
                        {Array.from({ length: review.rating }).map((_, i) => (
                          <Star key={i} className="w-3 h-3 text-warn-500 fill-warn-500" />
                        ))}
                        <span className="text-xs text-ink-400 ml-1">{review.createdAt}</span>
                      </div>
                    </div>
                    <p className="text-sm text-ink-600 leading-relaxed">{review.text}</p>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Sticky bottom CTA */}
      <div className="fixed bottom-0 left-0 right-0 bg-white border-t border-ink-200 px-4 py-4 flex gap-3 max-w-4xl mx-auto">
        <Button variant="outline" size="lg" className="flex-1" onClick={() => navigate('/chat')}>
          <MessageCircle className="w-4 h-4" />
          Free Chat
        </Button>
        <Button variant="primary" size="lg" className="flex-1" onClick={() => navigate(`/book/${MOCK_ADVISOR.id}`)}>
          <Video className="w-4 h-4" />
          Book Video $39
        </Button>
      </div>
      {/* Spacer for sticky bar */}
      <div className="h-24" />
    </div>
  )
}
