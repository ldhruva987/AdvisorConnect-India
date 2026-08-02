import { useEffect, useState } from 'react'

/** How often the clock is re-read. A minute is fine for date-level cutoffs. */
export const CLOCK_REFRESH_MS = 60_000

/**
 * The current time as a value that is stable *within* a render.
 *
 * Reading `Date.now()` inline while rendering makes any derived list
 * non-idempotent — two renders with identical props can disagree — which is
 * exactly what `react-hooks/purity` flags. Ticking it from an effect keeps the
 * render pure and still gets the behaviour the inline read was reaching for: a
 * tab left open overnight stops classifying a session that has already started
 * as "upcoming".
 *
 * Extracted from AdvisorDashboardPage when MyBookingsPage needed the same
 * cutoff; two private copies would have been free to drift apart.
 */
export function useNow(): number {
  const [now, setNow] = useState(() => Date.now())

  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), CLOCK_REFRESH_MS)
    return () => clearInterval(id)
  }, [])

  return now
}
