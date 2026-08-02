import { useCallback, useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { Search, SearchX } from 'lucide-react'
import { Navbar } from '@/shared/components/layout/Navbar'
import { AdvisorCard } from '@/features/explore/components/AdvisorCard'
import { advisorsQueryKey, useAdvisors } from '@/features/explore/hooks/useAdvisors'
import { Button } from '@/shared/components/ui/Button'
import { Input } from '@/shared/components/ui/Input'
import { EmptyState } from '@/shared/components/ui/EmptyState'
import { ErrorBanner } from '@/shared/components/ui/ErrorBanner'
import { SkeletonCard } from '@/shared/components/ui/Skeleton'
import { ALL_SECTORS, SECTOR_LABELS, isAdvisorSectorEnum } from '@/lib/sectors'
import type { AdvisorSectorEnum } from '@/lib/sectors'
import type { AdvisorPublic } from '@/types'

const SORT_OPTIONS = [
  { value: 'rating', label: 'Top Rated' },
  { value: 'reviews', label: 'Most Reviewed' },
  { value: 'response', label: 'Fastest Response' },
  { value: 'online', label: 'Online Now' },
] as const

type SortValue = (typeof SORT_OPTIONS)[number]['value']

/** How many placeholder cards to show while the first page loads. */
const SKELETON_COUNT = 8

/** Delay before a keystroke becomes a URL write + a new request. */
const SEARCH_DEBOUNCE_MS = 300

/**
 * Re-sorts the page the server already returned.
 *
 * `GET /advisors` has no sort parameter today, so this deliberately reorders
 * only the current page rather than pretending to sort the whole result set.
 * When the backend grows a `sort` param this should become a query param and
 * this function should go away.
 */
function sortAdvisors(advisors: AdvisorPublic[], sortBy: SortValue): AdvisorPublic[] {
  const list = [...advisors]
  switch (sortBy) {
    case 'rating':
      return list.sort((a, b) => b.rating - a.rating)
    case 'reviews':
      return list.sort((a, b) => b.reviewCount - a.reviewCount)
    case 'response':
      return list.sort((a, b) => a.responseTimeMinutes - b.responseTimeMinutes)
    case 'online':
      return list.sort((a, b) => Number(b.isOnline) - Number(a.isOnline))
  }
}

export function ExplorePage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const queryClient = useQueryClient()

  // ── URL is the source of truth for the query ──────────────────────────────
  // Filters live in the search params so `/explore?sector=CAREER` from the
  // landing page's category pills actually arrives filtered, and so a filtered
  // list is shareable and survives a reload.
  const rawSector = searchParams.get('sector')
  const sector: AdvisorSectorEnum | undefined =
    rawSector && isAdvisorSectorEnum(rawSector) ? rawSector : undefined
  const q = searchParams.get('q') ?? ''
  const pageParam = Number(searchParams.get('page'))
  const page = Number.isInteger(pageParam) && pageParam > 0 ? pageParam : 0

  const [sortBy, setSortBy] = useState<SortValue>('rating')
  // The input is local so typing stays responsive; the debounce below is what
  // promotes it into the URL (and therefore into a request).
  const [searchInput, setSearchInput] = useState(q)

  const updateParams = useCallback(
    (changes: Record<string, string | undefined>, options?: { replace?: boolean }) => {
      setSearchParams(
        (prev) => {
          const next = new URLSearchParams(prev)
          for (const [key, value] of Object.entries(changes)) {
            if (value === undefined || value === '') next.delete(key)
            else next.set(key, value)
          }
          return next
        },
        options,
      )
    },
    [setSearchParams],
  )

  useEffect(() => {
    if (searchInput === q) return
    const timer = setTimeout(() => {
      // `replace` so a search doesn't leave one history entry per keystroke.
      // Page resets: results for "anxiety" have nothing to do with page 3 of
      // the unfiltered list.
      updateParams({ q: searchInput, page: undefined }, { replace: true })
    }, SEARCH_DEBOUNCE_MS)
    return () => clearTimeout(timer)
  }, [searchInput, q, updateParams])

  const params = { sector, q: q || undefined, page: page || undefined }
  const { advisors, totalElements, totalPages, isLoading, isError, errorMessage } =
    useAdvisors(params)

  const sorted = useMemo(() => sortAdvisors(advisors, sortBy), [advisors, sortBy])

  const handleSectorClick = (value: AdvisorSectorEnum | undefined) => {
    updateParams({ sector: value, page: undefined })
  }

  const handleRetry = () => {
    // `useAdvisors` returns a flattened shape without `refetch`, so the retry
    // goes through the exported key instead of reaching into the hook.
    void queryClient.invalidateQueries({ queryKey: advisorsQueryKey(params) })
  }

  const clearFilters = () => {
    setSearchInput('')
    updateParams({ sector: undefined, q: undefined, page: undefined })
  }

  const hasFilters = Boolean(sector) || q !== ''
  const activeLabel = sector ? SECTOR_LABELS[sector] : null

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
              aria-label="Search advisors"
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              leftIcon={<Search className="w-4 h-4" />}
            />
          </div>

          {/* Category pills — driven by the shared sector vocabulary, so a new
              backend enum value shows up here without a second edit. */}
          <div className="flex flex-wrap gap-2 mb-4">
            <button
              onClick={() => handleSectorClick(undefined)}
              aria-pressed={!sector}
              className={`px-4 py-1.5 rounded-full text-sm font-medium border transition-all ${
                !sector
                  ? 'bg-oxblood-700 text-white border-oxblood-700'
                  : 'bg-white text-ink-600 border-ink-200 hover:border-oxblood-700 hover:text-oxblood-700'
              }`}
            >
              All
            </button>
            {ALL_SECTORS.map((value) => (
              <button
                key={value}
                onClick={() => handleSectorClick(value)}
                aria-pressed={sector === value}
                className={`px-4 py-1.5 rounded-full text-sm font-medium border transition-all ${
                  sector === value
                    ? 'bg-oxblood-700 text-white border-oxblood-700'
                    : 'bg-white text-ink-600 border-ink-200 hover:border-oxblood-700 hover:text-oxblood-700'
                }`}
              >
                {SECTOR_LABELS[value]}
              </button>
            ))}
          </div>

          {/* Sort + count row */}
          <div className="flex items-center justify-between">
            <p className="text-sm text-ink-500">
              {isLoading ? (
                'Loading advisors…'
              ) : (
                <>
                  <span className="font-semibold text-ink-900">
                    {totalElements} {totalElements === 1 ? 'advisor' : 'advisors'}
                  </span>{' '}
                  available
                  {activeLabel && ` · ${activeLabel}`}
                </>
              )}
            </p>
            <label className="flex items-center gap-2 text-sm text-ink-500">
              <span className="sr-only">Sort advisors</span>
              <select
                value={sortBy}
                onChange={(e) => setSortBy(e.target.value as SortValue)}
                aria-label="Sort advisors"
                className="text-sm border border-ink-200 rounded-lg px-3 py-1.5 text-ink-700 bg-white focus:outline-none focus:ring-2 focus:ring-oxblood-700"
              >
                {SORT_OPTIONS.map((o) => (
                  <option key={o.value} value={o.value}>{o.label}</option>
                ))}
              </select>
            </label>
          </div>
        </div>
      </div>

      {/* Grid */}
      <div className="max-w-7xl mx-auto px-4 py-8">
        {isError ? (
          <ErrorBanner
            message={errorMessage ?? 'Something went wrong. Please try again.'}
            onRetry={handleRetry}
          />
        ) : isLoading ? (
          <div className="grid sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-5">
            {Array.from({ length: SKELETON_COUNT }, (_, i) => (
              <SkeletonCard key={i} />
            ))}
          </div>
        ) : sorted.length === 0 ? (
          <EmptyState
            icon={<SearchX className="w-8 h-8" />}
            title="No advisors found"
            description="Try adjusting your search or filters"
            action={
              hasFilters ? (
                <Button variant="outline" onClick={clearFilters}>
                  Clear filters
                </Button>
              ) : undefined
            }
          />
        ) : (
          <>
            <div className="grid sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-5">
              {sorted.map((advisor) => (
                <AdvisorCard key={advisor.id} advisor={advisor} />
              ))}
            </div>

            {totalPages > 1 && (
              <nav
                aria-label="Pagination"
                className="flex items-center justify-center gap-4 mt-10"
              >
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page <= 0}
                  onClick={() => updateParams({ page: page - 1 === 0 ? undefined : String(page - 1) })}
                >
                  Previous
                </Button>
                <span className="text-sm text-ink-500">
                  Page {page + 1} of {totalPages}
                </span>
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page >= totalPages - 1}
                  onClick={() => updateParams({ page: String(page + 1) })}
                >
                  Next
                </Button>
              </nav>
            )}
          </>
        )}
      </div>
    </div>
  )
}
