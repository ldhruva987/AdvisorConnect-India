import { cn, getInitials, getAvatarColor } from '@/lib/utils'

interface AvatarProps {
  username: string
  color?: string
  size?: 'sm' | 'md' | 'lg' | 'xl'
  showOnline?: boolean
  className?: string
}

const sizeMap = {
  sm:  'w-8  h-8  text-xs',
  md:  'w-10 h-10 text-sm',
  lg:  'w-14 h-14 text-xl',
  xl:  'w-20 h-20 text-2xl',
}

/** Initials-based coloured avatar — matches prototype design */
export function Avatar({ username, color, size = 'md', showOnline = false, className }: AvatarProps) {
  const bg = color ?? getAvatarColor(username)
  const initials = getInitials(username)

  return (
    <div className={cn('relative flex-shrink-0', className)}>
      <div
        className={cn('rounded-full flex items-center justify-center text-white font-bold', sizeMap[size])}
        style={{ background: bg }}
      >
        {initials}
      </div>
      {showOnline && (
        <span className="absolute -bottom-0.5 -right-0.5 w-3 h-3 bg-pine-600 rounded-full ring-2 ring-white" />
      )}
    </div>
  )
}
