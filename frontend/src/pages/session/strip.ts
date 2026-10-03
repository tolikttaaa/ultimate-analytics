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

/** Shortest segment the server keeps (`Segment.MIN_DURATION_SEC`); shorter leftovers of a cut are removed. */
export const MIN_SEGMENT_SEC = 10

/** What saving a window does to a segment it overlaps (the server's overwrite, spec 9.1). */
export type Cut =
  | { segment: Segment; kind: 'split' }
  | { segment: Segment; kind: 'shortened'; to: TimeWindow }
  | { segment: Segment; kind: 'replaced' }

/** The segments a window overlaps, and what saving it does to each. */
export function cutsBy([from, to]: TimeWindow, segments: Segment[]): Cut[] {
  return [...segments].sort((a, b) => a.startT - b.startT).flatMap((segment): Cut[] => {
    if (segment.endT <= from || segment.startT >= to) return []
    const left: TimeWindow = [segment.startT, Math.min(from, segment.endT)]
    const right: TimeWindow = [Math.max(to, segment.startT), segment.endT]
    const parts = [left, right].filter(([a, b]) => b - a >= MIN_SEGMENT_SEC)
    if (parts.length === 2) return [{ segment, kind: 'split' }]
    if (parts.length === 1) return [{ segment, kind: 'shortened', to: parts[0] }]
    return [{ segment, kind: 'replaced' }]
  })
}
