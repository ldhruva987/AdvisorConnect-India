import { cn } from '@/lib/utils'
import { cva, type VariantProps } from 'class-variance-authority'

const badgeVariants = cva(
  'inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold transition-colors',
  {
    variants: {
      variant: {
        default:   'bg-ink-100 text-ink-700',
        brand:     'bg-oxblood-50 text-oxblood-700 border border-oxblood-100',
        success:   'bg-pine-100 text-pine-600',
        danger:    'bg-danger-100 text-danger-600',
        warn:      'bg-warn-100 text-warn-600',
        pending:   'bg-warn-100 text-warn-600',
        approved:  'bg-pine-100 text-pine-600',
        rejected:  'bg-danger-100 text-danger-600',
        free:      'bg-pine-100 text-pine-600',
        live:      'bg-pine-100 text-pine-600',
      },
    },
    defaultVariants: { variant: 'default' },
  },
)

interface BadgeProps extends VariantProps<typeof badgeVariants> {
  children: React.ReactNode
  className?: string
}

export function Badge({ variant, children, className }: BadgeProps) {
  return <span className={cn(badgeVariants({ variant }), className)}>{children}</span>
}

/** Maps ApplicationStatus → Badge variant */
export function StatusBadge({ status }: { status: string }) {
  const map: Record<string, VariantProps<typeof badgeVariants>['variant']> = {
    PENDING:        'pending',
    UNDER_REVIEW:   'warn',
    APPROVED:       'approved',
    REJECTED:       'rejected',
    NEEDS_MORE_INFO:'warn',
  }
  const labels: Record<string, string> = {
    PENDING:        'Pending',
    UNDER_REVIEW:   'Under Review',
    APPROVED:       'Approved',
    REJECTED:       'Rejected',
    NEEDS_MORE_INFO:'Needs Info',
  }
  return <Badge variant={map[status] ?? 'default'}>{labels[status] ?? status}</Badge>
}
