import { isAdvisorSectorEnum, sectorEnumToLabel } from '@/lib/sectors'
import { getAvatarColor } from '@/lib/utils'
import type { AdvisorPublicDto } from '@/types/api'
import type { AdvisorPublic, AdvisorSector } from '@/types'

/**
 * `AdvisorPublicDto` (wire) → `AdvisorPublic` (UI).
 *
 * Three fields are simply named differently on either side
 * (`professionalTitle`/`title`, `averageRating`/`rating`,
 * `avatarColor`/`color`) and sectors arrive as backend enum values that the UI
 * renders as labels. Every field is defensively defaulted: a partially
 * populated advisor (no reviews yet, no colour assigned) must not crash a card.
 *
 * Living in `lib/` rather than a feature folder because both the explore list
 * and the profile page need it, and neither should import from the other.
 */
export function mapAdvisorDto(dto: AdvisorPublicDto): AdvisorPublic {
  return {
    id: dto.id,
    username: dto.username,
    title: dto.professionalTitle ?? '',
    bio: dto.bio ?? '',
    tags: dto.tags ?? [],
    rating: dto.averageRating ?? 0,
    reviewCount: dto.reviewCount ?? 0,
    chatCount: dto.chatCount ?? 0,
    responseTimeMinutes: dto.responseTimeMinutes ?? 0,
    experienceYears: dto.experienceYears ?? 0,
    isOnline: dto.isOnline ?? false,
    isVerified: dto.isVerified ?? false,
    // Fall back to the deterministic hash so an advisor always has *a* colour.
    color: dto.avatarColor || getAvatarColor(dto.username ?? ''),
    sectors: mapSectorEnums(dto.sectors),
    languages: dto.languages ?? [],
  }
}

/**
 * Enum sectors → display labels, dropping anything this frontend build doesn't
 * know about. A backend that ships a new enum value should render as "one
 * fewer tag", not as a crash or a raw `SOME_NEW_VALUE` chip.
 */
export function mapSectorEnums(sectors: string[] | undefined | null): AdvisorSector[] {
  return (sectors ?? []).filter(isAdvisorSectorEnum).map(sectorEnumToLabel)
}
