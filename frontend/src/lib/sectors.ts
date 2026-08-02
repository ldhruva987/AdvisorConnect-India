import type { AdvisorSector } from '@/types'

/**
 * Canonical sector vocabulary.
 *
 * The backend speaks the Java `AdvisorSector` enum (`MENTAL_HEALTH`); the UI
 * speaks display labels (`'Mental Health'`, the `AdvisorSector` union in
 * `@/types`). Before this module, three pages each hand-rolled their own
 * category array and they had already drifted apart. Everything that needs to
 * iterate sectors or translate between the two vocabularies goes through here.
 */
export type AdvisorSectorEnum =
  | 'CAREER'
  | 'RELATIONSHIPS'
  | 'FINANCE'
  | 'MENTAL_HEALTH'
  | 'LIFE_COACHING'
  | 'PARENTING'
  | 'HEALTH_WELLNESS'

/**
 * Enum → display label. Typed against the `AdvisorSector` label union rather
 * than bare `string`, so adding a backend enum value without adding its label
 * to `@/types` is a compile error instead of a runtime surprise.
 */
export const SECTOR_LABELS: Record<AdvisorSectorEnum, AdvisorSector> = {
  CAREER: 'Career',
  RELATIONSHIPS: 'Relationships',
  FINANCE: 'Finance',
  MENTAL_HEALTH: 'Mental Health',
  LIFE_COACHING: 'Life Coaching',
  PARENTING: 'Parenting',
  HEALTH_WELLNESS: 'Health & Wellness',
}

/** Single source of truth for iteration (filter pills, onboarding checkboxes). */
export const ALL_SECTORS = Object.keys(SECTOR_LABELS) as AdvisorSectorEnum[]

/** Reverse index, built from `SECTOR_LABELS` so the two can never drift. */
const LABEL_TO_ENUM = new Map<string, AdvisorSectorEnum>(
  ALL_SECTORS.map((value) => [SECTOR_LABELS[value], value]),
)

/** `'MENTAL_HEALTH'` → `'Mental Health'`. */
export function sectorEnumToLabel(value: AdvisorSectorEnum): AdvisorSector {
  return SECTOR_LABELS[value]
}

/**
 * `'Mental Health'` → `'MENTAL_HEALTH'`. Returns `undefined` for anything not
 * in the vocabulary (e.g. a stale `?sector=Business` deep link) so callers can
 * decide between ignoring the filter and showing an error.
 */
export function labelToSectorEnum(label: string): AdvisorSectorEnum | undefined {
  return LABEL_TO_ENUM.get(label)
}

/** Narrowing guard for untrusted strings (URL params, API payloads). */
export function isAdvisorSectorEnum(value: string): value is AdvisorSectorEnum {
  return Object.prototype.hasOwnProperty.call(SECTOR_LABELS, value)
}
