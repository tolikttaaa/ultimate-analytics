import { describe, expect, it } from 'vitest'
import { segment } from '../../test/data'
import { edgeLimits, nextSegment, timeAt } from './strip'

const strip = { left: 100, width: 1000 }

describe('timeAt', () => {
  it('maps a position of the strip to whole seconds of the view', () => {
    expect(timeAt(100, strip, [0, 2000])).toBe(0)
    expect(timeAt(600, strip, [0, 2000])).toBe(1000)
    expect(timeAt(350, strip, [600, 1000])).toBe(700)
    expect(timeAt(351, strip, [0, 1000])).toBe(251)
  })

  it('stays inside the view', () => {
    expect(timeAt(0, strip, [600, 1000])).toBe(600)
    expect(timeAt(5000, strip, [600, 1000])).toBe(1000)
  })
})

describe('edgeLimits', () => {
  const segments = [segment({ id: 'b', startT: 500, endT: 900 }), segment({ id: 'a', startT: 0, endT: 300 }), segment({ id: 'c', startT: 1200, endT: 1500 })]

  it('keeps an edge between the neighbours and a second away from the other edge', () => {
    expect(edgeLimits(segments, segments[0], 'start', 1800)).toEqual([300, 899])
    expect(edgeLimits(segments, segments[0], 'end', 1800)).toEqual([501, 1200])
  })

  it('lets the first and last segment reach the ends of the session', () => {
    expect(edgeLimits(segments, segments[1], 'start', 1800)).toEqual([0, 299])
    expect(edgeLimits(segments, segments[2], 'end', 1800)).toEqual([1201, 1800])
  })

  it('finds the next segment', () => {
    expect(nextSegment(segments, segments[1])?.id).toBe('b')
    expect(nextSegment(segments, segments[2])).toBeUndefined()
  })
})
