import { cn } from '@/lib/utils'

/**
 * Loading placeholder. Flat by default per DESIGN.md's flat-by-default rule
 * (skeletons sit inside normal document flow, so no shadow), and the pulse is
 * scoped to `motion-safe:` so `prefers-reduced-motion` users get a static bar
 * instead of an animation they can't turn off.
 */
export function Skeleton({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      aria-hidden="true"
      className={cn('bg-ink-100 rounded-md motion-safe:animate-pulse', className)}
      {...props}
    />
  )
}

/**
 * Card-shaped placeholder mirroring `AdvisorCard`'s layout (avatar + name/title
 * block, two bio lines, a tag row, two buttons) so the grid doesn't reflow when
 * real data arrives.
 */
export function SkeletonCard({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      role="status"
      aria-label="Loading advisor"
      className={cn('bg-ink-50 rounded-xl border border-ink-200 p-5 flex flex-col h-full', className)}
      {...props}
    >
      <div className="flex items-start gap-3 mb-3">
        <Skeleton className="w-12 h-12 rounded-full flex-shrink-0" />
        <div className="flex-1 space-y-2 pt-0.5">
          <Skeleton className="h-3.5 w-2/5" />
          <Skeleton className="h-3 w-3/5" />
          <Skeleton className="h-3 w-1/3" />
        </div>
      </div>

      <div className="space-y-2 mb-4 flex-1">
        <Skeleton className="h-3 w-full" />
        <Skeleton className="h-3 w-4/5" />
      </div>

      <div className="flex gap-1.5 mb-4">
        <Skeleton className="h-5 w-16 rounded-full" />
        <Skeleton className="h-5 w-20 rounded-full" />
      </div>

      <div className="flex gap-2 mt-auto">
        <Skeleton className="h-8 flex-1 rounded-lg" />
        <Skeleton className="h-8 flex-1 rounded-lg" />
      </div>
    </div>
  )
}

interface SkeletonRowProps extends React.HTMLAttributes<HTMLTableRowElement> {
  /** Number of `<td>` cells to emit. Match the table's column count. */
  columns?: number
}

/**
 * Table-row placeholder for the admin tables. Renders a real `<tr>`/`<td>`, so
 * it must be mounted inside a `<tbody>`.
 */
export function SkeletonRow({ columns = 5, className, ...props }: SkeletonRowProps) {
  return (
    <tr className={cn('border-b border-ink-100', className)} {...props}>
      {Array.from({ length: columns }, (_, index) => (
        <td key={index} className="px-4 py-3">
          <Skeleton className={cn('h-3', index === 0 ? 'w-32' : 'w-20')} />
        </td>
      ))}
    </tr>
  )
}
