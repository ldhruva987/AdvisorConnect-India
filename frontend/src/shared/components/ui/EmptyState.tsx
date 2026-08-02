import type { ReactNode } from 'react'
import { cn } from '@/lib/utils'

interface EmptyStateProps {
  /** Usually a lucide icon; rendered decoratively. */
  icon?: ReactNode
  title: string
  description?: string
  /** Usually a `<Button>` that resolves the emptiness (clear filters, invite). */
  action?: ReactNode
  className?: string
}

/**
 * Generalised from ExplorePage's inline "No advisors found" block, which was
 * the only empty state in the app. Same visual treatment, now reusable for
 * empty booking lists, chat inboxes, and admin queues.
 */
export function EmptyState({ icon, title, description, action, className }: EmptyStateProps) {
  return (
    <div className={cn('text-center py-16 text-ink-400', className)}>
      {icon && (
        <div className="flex justify-center mb-3 text-ink-300" aria-hidden="true">
          {icon}
        </div>
      )}
      <p className="text-lg font-medium">{title}</p>
      {description && <p className="text-sm mt-1">{description}</p>}
      {action && <div className="mt-5 flex justify-center">{action}</div>}
    </div>
  )
}
