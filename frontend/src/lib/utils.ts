import { type ClassValue, clsx } from 'clsx'
import { twMerge } from 'tailwind-merge'

/** Merge Tailwind classes safely — shadcn/ui cn() pattern */
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

/** Format currency */
export function formatCurrency(amount: number): string {
  return new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' }).format(amount)
}

/** Generate initials from username */
export function getInitials(username: string): string {
  return username.replace('@', '').slice(0, 2).toUpperCase()
}

/** Generate deterministic color from string — muted jewel tones, same visual weight as the brand palette */
const AVATAR_COLORS = [
  '#8a3f24', '#784f00', '#516000', '#006970',
  '#005e8f', '#4d4f94', '#73417e', '#843b61',
]
export function getAvatarColor(str: string): string {
  let hash = 0
  for (let i = 0; i < str.length; i++) hash = str.charCodeAt(i) + ((hash << 5) - hash)
  return AVATAR_COLORS[Math.abs(hash) % AVATAR_COLORS.length]
}

/** Truncate text */
export function truncate(text: string, maxLength: number): string {
  return text.length <= maxLength ? text : text.slice(0, maxLength) + '…'
}
