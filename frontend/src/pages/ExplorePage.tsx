import { useState, useMemo } from 'react'
import { Search } from 'lucide-react'
import { Navbar } from '@/shared/components/layout/Navbar'
import { AdvisorCard } from '@/features/explore/components/AdvisorCard'
import { Input } from '@/shared/components/ui/Input'
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
    color: '#8a3f24',
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
    color: '#784f00',
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
    color: '#516000',
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
    color: '#006970',
    sectors: ['Relationships'],
    languages: ['English', 'French'],
  },
  {
    id: '5',
    username: '@DrDiwakarK',
    title: 'Psychiatrist & Therapist',
    bio: 'Board-certified psychiatrist offering evidence-based therapy for trauma, PTSD, and mood disorders. Compassionate, non-judgmental approach.',
    tags: ['Mental Health', 'Trauma', 'PTSD'],
    rating: 4.7,
    reviewCount: 201,
    chatCount: 778,
    responseTimeMinutes: 15,
    experienceYears: 15,
    isOnline: false,
    isVerified: true,
    color: '#005e8f',
    sectors: ['Mental Health'],
    languages: ['English', 'Hindi'],
  },
  {
    id: '6',
    username: '@RealtorPriya',
    title: 'Financial Planner & RE Advisor',
    bio: 'Helping first-time buyers and investors navigate real estate finance, mortgages, and long-term wealth planning.',
    tags: ['Finance', 'Real Estate', 'Mortgages'],
    rating: 4.8,
    reviewCount: 134,
    chatCount: 445,
    responseTimeMinutes: 12,
    experienceYears: 9,
    isOnline: true,
    isVerified: true,
    color: '#4d4f94',
    sectors: ['Finance'],
    languages: ['English'],
  },
  {
    id: '7',
    username: '@NatalieLifeCoach',
    title: 'Life & Relationship Coach',
    bio: 'ICF-certified coach helping individuals build confidence, navigate breakups, and redesign their life direction with purpose.',
    tags: ['Relationships', 'Life Coaching', 'Confidence'],
    rating: 4.6,
    reviewCount: 97,
    chatCount: 389,
    responseTimeMinutes: 6,
    experienceYears: 5,
    isOnline: true,
    isVerified: false,
    color: '#73417e',
    sectors: ['Relationships', 'Career'],
    languages: ['English'],
  },
  {
    id: '8',
    username: '@AdvisorMarcus',
    title: 'Corporate Finance & Career Advisor',
    bio: 'Former Goldman Sachs analyst turned advisor. Helping ambitious young professionals understand money, investments, and career growth.',
    tags: ['Finance', 'Career', 'Investing'],
    rating: 4.7,
    reviewCount: 112,
    chatCount: 567,
    responseTimeMinutes: 18,
    experienceYears: 11,
    isOnline: false,
    isVerified: true,
    color: '#843b61',
    sectors: ['Finance', 'Career'],
    languages: ['English'],
  },
]

const CATEGORIES = ['All', 'Career', 'Relationships', 'Finance', 'Mental Health', 'Life Coaching']
const SORT_OPTIONS = [
  { value: 'rating', label: 'Top Rated' },
  { value: 'reviews', label: 'Most Reviewed' },
  { value: 'response', label: 'Fastest Response' },
  { value: 'online', label: 'Online Now' },
]

export function ExplorePage() {
  const [query, setQuery] = useState('')
  const [activeCategory, setActiveCategory] = useState('All')
  const [sortBy, setSortBy] = useState('rating')

  const filtered = useMemo(() => {
    let list = [...MOCK_ADVISORS]

    if (activeCategory !== 'All') {
      list = list.filter((a) =>
        a.sectors.some((s) => s.toLowerCase().includes(activeCategory.toLowerCase())) ||
        a.tags.some((t) => t.toLowerCase().includes(activeCategory.toLowerCase()))
      )
    }

    if (query.trim()) {
      const q = query.toLowerCase()
      list = list.filter(
        (a) =>
          a.username.toLowerCase().includes(q) ||
          a.title.toLowerCase().includes(q) ||
          a.bio.toLowerCase().includes(q) ||
          a.tags.some((t) => t.toLowerCase().includes(q))
      )
    }

    switch (sortBy) {
      case 'rating':
        list.sort((a, b) => b.rating - a.rating)
        break
      case 'reviews':
        list.sort((a, b) => b.reviewCount - a.reviewCount)
        break
      case 'response':
        list.sort((a, b) => a.responseTimeMinutes - b.responseTimeMinutes)
        break
      case 'online':
        list.sort((a, b) => (b.isOnline ? 1 : 0) - (a.isOnline ? 1 : 0))
        break
    }

    return list
  }, [query, activeCategory, sortBy])

  return (
    <div className="pt-16 min-h-screen bg-ink-50">
      <Navbar />

      {/* Page header */}
      <div className="bg-white border-b border-ink-200">
        <div className="max-w-7xl mx-auto px-4 py-8">
          <h1 className="font-heading font-medium text-3xl text-ink-900 mb-6">Find an Advisor</h1>

          {/* Search */}
          <div className="max-w-xl mb-6">
            <Input
              placeholder="Search by name, topic, or expertise..."
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              leftIcon={<Search className="w-4 h-4" />}
            />
          </div>

          {/* Category pills */}
          <div className="flex flex-wrap gap-2 mb-4">
            {CATEGORIES.map((cat) => (
              <button
                key={cat}
                onClick={() => setActiveCategory(cat)}
                className={`px-4 py-1.5 rounded-full text-sm font-medium border transition-all ${
                  activeCategory === cat
                    ? 'bg-oxblood-700 text-white border-oxblood-700'
                    : 'bg-white text-ink-600 border-ink-200 hover:border-oxblood-700 hover:text-oxblood-700'
                }`}
              >
                {cat}
              </button>
            ))}
          </div>

          {/* Sort + count row */}
          <div className="flex items-center justify-between">
            <p className="text-sm text-ink-500">
              <span className="font-semibold text-ink-900">248 advisors</span> available
              {activeCategory !== 'All' && ` · ${activeCategory}`}
            </p>
            <select
              value={sortBy}
              onChange={(e) => setSortBy(e.target.value)}
              className="text-sm border border-ink-200 rounded-lg px-3 py-1.5 text-ink-700 bg-white focus:outline-none focus:ring-2 focus:ring-oxblood-700"
            >
              {SORT_OPTIONS.map((o) => (
                <option key={o.value} value={o.value}>{o.label}</option>
              ))}
            </select>
          </div>
        </div>
      </div>

      {/* Grid */}
      <div className="max-w-7xl mx-auto px-4 py-8">
        {filtered.length === 0 ? (
          <div className="text-center py-16 text-ink-400">
            <p className="text-lg font-medium">No advisors found</p>
            <p className="text-sm mt-1">Try adjusting your search or filters</p>
          </div>
        ) : (
          <div className="grid sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-5">
            {filtered.map((advisor) => (
              <AdvisorCard key={advisor.id} advisor={advisor} />
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
