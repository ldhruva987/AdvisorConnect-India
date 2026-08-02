import { cn } from '@/lib/utils'

interface ErrorBannerProps {
  message: string
  /** When supplied, renders a "Try again" affordance. */
  onRetry?: () => void
  className?: string
}

/**
 * The single error-box treatment for the app. Consolidates the identical inline
 * markup that LoginPage and RegisterPage each had a copy of, and adds the
 * `role="alert"` neither had, so screen readers announce failures.
 *
 * Danger tokens only. Oxblood is reserved for primary CTAs per DESIGN.md.
 */
export function ErrorBanner({ message, onRetry, className }: ErrorBannerProps) {
  return (
    <div
      role="alert"
      className={cn(
        'bg-danger-100 border border-danger-600/20 rounded-xl px-4 py-3 text-sm text-danger-600 font-medium',
        onRetry && 'flex items-center justify-between gap-4',
        className,
      )}
    >
      <span>{message}</span>
      {onRetry && (
        <button
          type="button"
          onClick={onRetry}
          className="flex-shrink-0 underline underline-offset-2 font-semibold hover:no-underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-danger-600 focus-visible:ring-offset-2 rounded"
        >
          Try again
        </button>
      )}
    </div>
  )
}
