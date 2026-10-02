import type { Segment } from '../../api/types'
import type { TimeWindow } from './timeWindow'

/*
 * Geometry of the segment strip: it shows the [from, to] of t the charts show, across its width.
 */

/** The t at a horizontal position of the strip, rounded to whole seconds and kept inside the view. */
export function timeAt(clientX: number, strip: { left: number; width: number }, [from, to]: TimeWindow): number {
  const share = strip.width > 0 ? (clientX - strip.left) / strip.width : 0
  return Math.round(Math.min(to, Math.max(from, from + share * (to - from))))
}

/**
 * Where an edge of a segment may go: between its neighbours (or the session) and at least one second away from its
 * other edge.
 */
export function edgeLimits(segments: Segment[], segment: Segment, edge: 'start' | 'end', lastT: number): [number, number] {
  const sorted = [...segments].sort((a, b) => a.startT - b.startT)
  const index = sorted.findIndex((s) => s.id === segment.id)
  if (edge === 'start') return [sorted[index - 1]?.endT ?? 0, segment.endT - 1]
  return [segment.startT + 1, sorted[index + 1]?.startT ?? lastT]
}

/** The segment after this one, if any. */
export function nextSegment(segments: Segment[], segment: Segment): Segment | undefined {
  return [...segments].sort((a, b) => a.startT - b.startT).find((s) => s.startT >= segment.endT)
}
