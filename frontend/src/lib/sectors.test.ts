import { describe, expect, it } from 'vitest'
import {
  ALL_SECTORS,
  SECTOR_LABELS,
  isAdvisorSectorEnum,
  labelToSectorEnum,
  sectorEnumToLabel,
  type AdvisorSectorEnum,
} from './sectors'

describe('sectors', () => {
  it('covers all seven backend enum values', () => {
    expect(ALL_SECTORS).toHaveLength(7)
    expect(ALL_SECTORS).toEqual([
      'CAREER',
      'RELATIONSHIPS',
      'FINANCE',
      'MENTAL_HEALTH',
      'LIFE_COACHING',
      'PARENTING',
      'HEALTH_WELLNESS',
    ])
  })

  it('has a label for every enum value and no extras', () => {
    expect(Object.keys(SECTOR_LABELS)).toHaveLength(ALL_SECTORS.length)
    for (const value of ALL_SECTORS) {
      expect(SECTOR_LABELS[value]).toBeTruthy()
    }
  })

  it.each(ALL_SECTORS)('round-trips %s through its label and back', (value) => {
    const label = sectorEnumToLabel(value)
    expect(typeof label).toBe('string')
    expect(labelToSectorEnum(label)).toBe(value)
  })

  it('maps the underscore-and-ampersand cases to human labels', () => {
    expect(sectorEnumToLabel('MENTAL_HEALTH')).toBe('Mental Health')
    expect(sectorEnumToLabel('HEALTH_WELLNESS')).toBe('Health & Wellness')
    expect(sectorEnumToLabel('LIFE_COACHING')).toBe('Life Coaching')
  })

  it('produces unique labels so the reverse map is lossless', () => {
    const labels = ALL_SECTORS.map(sectorEnumToLabel)
    expect(new Set(labels).size).toBe(labels.length)
  })

  it('returns undefined for labels outside the vocabulary', () => {
    // 'Business' is the bogus OnboardingPage entry with no backend equivalent.
    expect(labelToSectorEnum('Business')).toBeUndefined()
    expect(labelToSectorEnum('')).toBeUndefined()
    expect(labelToSectorEnum('mental health')).toBeUndefined()
    // The enum value itself is not a label.
    expect(labelToSectorEnum('MENTAL_HEALTH')).toBeUndefined()
  })

  it('guards untrusted strings', () => {
    expect(isAdvisorSectorEnum('CAREER')).toBe(true)
    expect(isAdvisorSectorEnum('Career')).toBe(false)
    expect(isAdvisorSectorEnum('BUSINESS')).toBe(false)
    // Must not be fooled by Object.prototype members.
    expect(isAdvisorSectorEnum('toString')).toBe(false)
    expect(isAdvisorSectorEnum('constructor')).toBe(false)
  })

  it('narrows correctly when used as a guard', () => {
    const raw = 'PARENTING'
    if (isAdvisorSectorEnum(raw)) {
      const narrowed: AdvisorSectorEnum = raw
      expect(sectorEnumToLabel(narrowed)).toBe('Parenting')
    } else {
      throw new Error('expected PARENTING to narrow')
    }
  })
})
