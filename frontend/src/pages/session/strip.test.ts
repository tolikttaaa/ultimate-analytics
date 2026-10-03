import { describe, expect, it } from 'vitest'
import { segment } from '../../test/data'
import { cutsBy, edgeLimits, nextSegment, timeAt } from './strip'

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

describe('cutsBy', () => {
  const lap1 = segment({ id: 'l1', startT: 0, endT: 3994 })
  const lap2 = segment({ id: 'l2', startT: 3994, endT: 7800 })

  it('splits a segment the window lies inside', () => {
    expect(cutsBy([600, 1200], [lap2, lap1])).toEqual([{ segment: lap1, kind: 'split' }])
  })

  it('shortens segments the window reaches into, and replaces covered ones', () => {
    expect(cutsBy([3000, 5000], [lap1, lap2])).toEqual([
      { segment: lap1, kind: 'shortened', to: [0, 3000] },
      { segment: lap2, kind: 'shortened', to: [5000, 7800] },
    ])
    expect(cutsBy([0, 7800], [lap1, lap2]).map((cut) => cut.kind)).toEqual(['replaced', 'replaced'])
  })

  it('counts leftovers shorter than 10 s as gone, like the server', () => {
    expect(cutsBy([5, 3990], [lap1])).toEqual([{ segment: lap1, kind: 'replaced' }])
    expect(cutsBy([5, 3000], [lap1])).toEqual([{ segment: lap1, kind: 'shortened', to: [3000, 3994] }])
  })

  it('leaves segments outside the window alone', () => {
    expect(cutsBy([4000, 5000], [lap1])).toEqual([])
    expect(cutsBy([3994, 5000], [lap1])).toEqual([])
  })
})
