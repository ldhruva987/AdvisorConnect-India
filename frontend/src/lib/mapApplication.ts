import { mapSectorEnums } from '@/lib/mapAdvisor'
import { getAvatarColor } from '@/lib/utils'
import type { AdvisorApplication } from '@/types'
import type { AdvisorApplicationSummaryDto } from '@/types/api'

/**
 * `AdvisorApplicationSummaryDto` (wire) → `AdvisorApplication` (UI).
 *
 * Same divergences as `mapAdvisor`, for the same reasons:
 * `professionalTitle`/`title`, `avatarColor`/`color`, and enum sectors that
 * the UI renders as labels. Kept alongside `mapAdvisor.ts` in `lib/` so all
 * DTO translation lives in one place rather than half in `lib/` and half in a
 * feature folder.
 *
 * `realNameBlurred` on the UI type is intentionally not populated: it is a
 * presentation concern for the admin table, not something the backend sends.
 */
export function mapApplicationDto(dto: AdvisorApplicationSummaryDto): AdvisorApplication {
  return {
    id: dto.id,
    username: dto.username,
    title: dto.professionalTitle ?? '',
    bio: dto.bio ?? '',
    sectors: mapSectorEnums(dto.sectors),
    qualification: dto.qualification ?? '',
    experienceYears: dto.experienceYears ?? '',
    previousWork: dto.previousWork ?? '',
    status: dto.status,
    submittedAt: dto.submittedAt,
    docCount: dto.docCount ?? 0,
    color: dto.avatarColor || getAvatarColor(dto.username ?? ''),
    legalName: dto.legalName,
  }
}
