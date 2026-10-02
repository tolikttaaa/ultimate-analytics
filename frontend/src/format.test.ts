import { describe, expect, it } from 'vitest'
import { distance, duration, gpsAccel, localDateTime, pace, speedKmh } from './format'

describe('format', () => {
  it('shows speed in km/h', () => {
    expect(speedKmh(6)).toBe('21.6 km/h')
    expect(speedKmh(null)).toBe('–')
  })

  it('shows pace as min:ss per km', () => {
    expect(pace(250)).toBe('4:10 /km')
    expect(pace(299.6)).toBe('5:00 /km')
    expect(pace(undefined)).toBe('–')
  })

  it('shows durations as h:mm:ss or m:ss', () => {
    expect(duration(3909)).toBe('1:05:09')
    expect(duration(247)).toBe('4:07')
    expect(duration(0)).toBe('0:00')
  })

  it('shows distances in m or km', () => {
    expect(distance(849.6)).toBe('850 m')
    expect(distance(5336.17)).toBe('5.34 km')
  })

  it('marks acceleration as GPS-derived', () => {
    expect(gpsAccel(2.04)).toBe('2.0 m/s² (GPS)')
  })

  it('shows a start time in the local time of the session', () => {
    expect(localDateTime('2026-09-30T16:07:47Z', 10_800)).toBe('2026-09-30 19:07')
    expect(localDateTime('2026-09-30T16:07:47Z', null)).toBe('2026-09-30 16:07')
  })
})
