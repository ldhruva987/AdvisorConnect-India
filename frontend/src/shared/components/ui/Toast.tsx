import { useEffect } from 'react'
import { cva } from 'class-variance-authority'
import { X } from 'lucide-react'
import { cn } from '@/lib/utils'
import { useToastStore, type Toast as ToastData } from '@/stores/toastStore'

/** How long a toast lingers before dismissing itself. */
const AUTO_DISMISS_MS = 5000

/**
 * Pine for success, danger for errors. Never oxblood — per DESIGN.md that is
 * reserved for primary CTAs, and a toast is not a call to action.
 *
 * Toasts sit outside document flow, so `shadow-toast` is the correct elevation
 * token here (see DESIGN.md's Elevation section) even though cards stay flat.
 */
const toastVariants = cva(
  'shadow-toast pointer-events-auto flex items-start gap-3 rounded-xl border px-4 py-3 text-sm font-medium',
  {
    variants: {
      variant: {
        success: 'bg-pine-100 text-pine-600 border-pine-600/20',
        error: 'bg-danger-100 text-danger-600 border-danger-600/20',
      },
    },
    defaultVariants: { variant: 'success' },
  },
)

export function Toast({ toast }: { toast: ToastData }) {
  const dismiss = useToastStore((s) => s.dismiss)

  useEffect(() => {
    const timeout = setTimeout(() => dismiss(toast.id), AUTO_DISMISS_MS)
    return () => clearTimeout(timeout)
  }, [toast.id, dismiss])

  return (
    <div
      // Errors interrupt; successes wait for a pause in screen-reader output.
      role={toast.variant === 'error' ? 'alert' : 'status'}
      aria-live={toast.variant === 'error' ? 'assertive' : 'polite'}
      className={cn(toastVariants({ variant: toast.variant }))}
    >
      <span className="flex-1">{toast.message}</span>
      <button
        type="button"
        onClick={() => dismiss(toast.id)}
        aria-label="Dismiss notification"
        className="flex-shrink-0 opacity-60 hover:opacity-100 transition-opacity focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-current focus-visible:ring-offset-1 rounded"
      >
        <X className="w-4 h-4" aria-hidden="true" />
      </button>
    </div>
  )
}

/**
 * Renders the toast stack. Mount exactly once, at the app root.
 * Renders nothing (not even the container) while the queue is empty so it can
 * never intercept clicks.
 */
export function Toaster() {
  const toasts = useToastStore((s) => s.toasts)

  if (toasts.length === 0) return null

  return (
    <div className="pointer-events-none fixed bottom-4 right-4 z-50 flex w-full max-w-sm flex-col gap-2 px-4 sm:px-0">
      {toasts.map((t) => (
        <Toast key={t.id} toast={t} />
      ))}
    </div>
  )
}
