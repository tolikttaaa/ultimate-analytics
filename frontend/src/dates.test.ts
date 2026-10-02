import { describe, expect, it } from 'vitest'
import { endOfDay, startOfDay } from './dates'

describe('dates', () => {
  it('turns calendar days into instants of the local time zone', () => {
    expect(startOfDay('2026-09-01')).toBe(new Date(2026, 8, 1).toISOString())
    expect(endOfDay('2026-09-30')).toBe(new Date(2026, 9, 1).toISOString())
    expect(endOfDay('2026-12-31')).toBe(new Date(2027, 0, 1).toISOString())
  })
})
