import { cn } from '@/lib/utils'
import { cva, type VariantProps } from 'class-variance-authority'
import { Loader2 } from 'lucide-react'

const buttonVariants = cva(
  'inline-flex items-center justify-center gap-2 font-semibold rounded-lg transition-colors duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-oxblood-600 focus-visible:ring-offset-2 disabled:opacity-40 disabled:cursor-not-allowed active:scale-[0.98]',
  {
    variants: {
      variant: {
        primary:  'bg-oxblood-600 text-white hover:bg-oxblood-700',
        outline:  'border border-ink-200 text-ink-800 hover:border-oxblood-600 hover:bg-oxblood-50 hover:text-oxblood-600',
        ghost:    'text-ink-600 hover:bg-ink-100 hover:text-ink-900',
        danger:   'bg-danger-100 text-danger-600 hover:bg-danger-600 hover:text-white',
        success:  'bg-pine-100 text-pine-600 hover:bg-pine-600 hover:text-white',
        dark:     'bg-ink-900 text-white hover:bg-ink-800',
      },
      size: {
        sm:   'text-xs  px-3   py-1.5',
        md:   'text-sm  px-5   py-2.5',
        lg:   'text-base px-8  py-3.5',
        icon: 'w-9 h-9',
      },
    },
    defaultVariants: { variant: 'primary', size: 'md' },
  },
)

interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {
  loading?: boolean
  fullWidth?: boolean
}

export function Button({ variant, size, loading, fullWidth, className, children, disabled, ...props }: ButtonProps) {
  return (
    <button
      className={cn(buttonVariants({ variant, size }), fullWidth && 'w-full', className)}
      disabled={disabled || loading}
      {...props}
    >
      {loading && <Loader2 className="w-4 h-4 animate-spin" />}
      {children}
    </button>
  )
}
