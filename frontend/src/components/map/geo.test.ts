import { describe, expect, it } from 'vitest'
import { effort, segment } from '../../test/data'
import { cursorPoint, effortStarts, geozoneOutline, MAX_LINE_POSITIONS, positionAt, segmentTracks, trackBounds, trackLines } from './geo'

/** Six seconds heading east, with a gap at t = 3. */
const series = {
  t: [0, 1, 2, 3, 4, 5],
  lat: [34.7, 34.7, 34.7, null, 34.7, 34.7001],
  lon: [33.1, 33.1001, 33.1002, null, 33.1004, 33.1005],
}

describe('trackLines', () => {
  it('draws one line per run of positions, broken in gaps', () => {
    expect(trackLines(series).geometry.coordinates).toEqual([
      [[33.1, 34.7], [33.1001, 34.7], [33.1002, 34.7]],
      [[33.1004, 34.7], [33.1005, 34.7001]],
    ])
  })

  it('cuts long runs into lines MapLibre can draw, without breaking the track', () => {
    const count = MAX_LINE_POSITIONS * 2 + 10
    const long = { t: [...Array(count).keys()], lat: Array(count).fill(34.7), lon: [...Array(count).keys()].map((i) => 33 + i / 1e5) }
    const lines = trackLines(long).geometry.coordinates
    expect(lines.map((line) => line.length)).toEqual([MAX_LINE_POSITIONS, MAX_LINE_POSITIONS, 12])
    expect(lines[1][0]).toEqual(lines[0][MAX_LINE_POSITIONS - 1])
    expect(lines.flat().length - (lines.length - 1)).toBe(count)
  })

  it('draws a window from the position at its start to the one at its end', () => {
    expect(trackLines(series, 1, 2).geometry.coordinates).toEqual([[[33.1001, 34.7], [33.1002, 34.7]]])
    expect(trackLines(series, 2, 4).geometry.coordinates).toEqual([])
  })
})

describe('segmentTracks', () => {
  const segments = [segment({ id: 'a', startT: 0, endT: 2 }), segment({ id: 'b', startT: 4, endT: 5, drillTypeId: null })]
  const colorOf = (s: { id: string }) => (s.id === 'a' ? '#e65100' : '#90a4ae')

  it('draws the part of the track of each segment in its colour', () => {
    const features = segmentTracks(series, segments, colorOf).features
    expect(features.map((feature) => [feature.properties, feature.geometry.coordinates])).toEqual([
      [{ segmentId: 'a', color: '#e65100' }, [[[33.1, 34.7], [33.1001, 34.7], [33.1002, 34.7]]]],
      [{ segmentId: 'b', color: '#90a4ae' }, [[[33.1004, 34.7], [33.1005, 34.7001]]]],
    ])
  })

  it('cuts the segments to a window and leaves out those outside it', () => {
    const features = segmentTracks(series, segments, colorOf, 1, 2).features
    expect(features.map((feature) => [feature.properties?.segmentId, feature.geometry.coordinates])).toEqual([
      ['a', [[[33.1001, 34.7], [33.1002, 34.7]]]],
    ])
  })
})

describe('positions', () => {
  it('are found by time, with null in gaps and outside the session', () => {
    expect(positionAt(series, 1)).toEqual([33.1001, 34.7])
    expect(positionAt(series, 1.4)).toEqual([33.1001, 34.7])
    expect(positionAt(series, 3)).toBeNull()
    expect(positionAt(series, 9)).toBeNull()
  })

  it('give the cursor point, or none', () => {
    expect(cursorPoint(series, 4).features[0].geometry.coordinates).toEqual([33.1004, 34.7])
    expect(cursorPoint(series, null).features).toEqual([])
    expect(cursorPoint(series, 3).features).toEqual([])
  })

  it('mark the start of every effort that has one', () => {
    const starts = effortStarts(series, [effort({ id: 'e1', startT: 2 }), effort({ id: 'e2', startT: 3 })])
    expect(starts.features.map((feature) => [feature.properties?.effortId, feature.geometry.coordinates]))
      .toEqual([['e1', [33.1002, 34.7]]])
  })

  it('bound the track', () => {
    expect(trackBounds(series)).toEqual([[33.1, 34.7], [33.1005, 34.7001]])
    expect(trackBounds({ t: [0], lat: [null], lon: [null] })).toBeNull()
  })
})

describe('geozoneOutline', () => {
  it('turns a circle into a closed ring at the radius', () => {
    const ring = geozoneOutline({ type: 'circle', lat: 60, lon: 10, radiusM: 1000 })!.geometry.coordinates[0]
    expect(ring).toHaveLength(65)
    expect(ring[0]).toEqual(ring[64])
    // North: 1 km is about 0.008993°; east at 60° N about twice as many degrees of longitude.
    expect(ring[0][1] - 60).toBeCloseTo(0.008993, 5)
    expect(ring[16][0] - 10).toBeCloseTo(0.017986, 5)
  })

  it('keeps a polygon as it is', () => {
    const coordinates = [[[33.1, 34.7], [33.2, 34.7], [33.2, 34.8], [33.1, 34.7]]]
    expect(geozoneOutline({ type: 'Polygon', coordinates })!.geometry.coordinates).toEqual(coordinates)
  })
})
