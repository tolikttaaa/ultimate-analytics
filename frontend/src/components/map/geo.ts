import type { Feature, FeatureCollection, MultiLineString, Point, Polygon, Position } from 'geojson'
import type { Effort, GeozoneShape, SessionSeries } from '../../api/types'

/*
 * GeoJSON for the maps, built from the series of the API: drawing only, no metrics (spec 7.2). Positions are
 * [lon, lat]; the series has one entry per second, t = 0..lastT.
 */

type Track = Pick<SessionSeries, 't' | 'lat' | 'lon'>

const EARTH_RADIUS_M = 6_371_008.8
const CIRCLE_VERTICES = 64
/**
 * MapLibre draws a line from at most 65 535 vertices, and a round-joined GPS line takes about ten per position; longer
 * runs are cut into lines of this many positions, each starting where the previous one ended.
 */
export const MAX_LINE_POSITIONS = 2000

function positionOf(series: Track, index: number): Position | null {
  const lat = series.lat[index]
  const lon = series.lon[index]
  return lat == null || lon == null ? null : [lon, lat]
}

/** The position at time t, or null in a gap or outside the session. */
export function positionAt(series: Track, t: number): Position | null {
  const index = Math.round(t) - (series.t[0] ?? 0)
  return index >= 0 && index < series.t.length ? positionOf(series, index) : null
}

/**
 * The track between the positions at `from` and `to`, as one line per run without gaps; second t moves from the
 * position at t to the one at t + 1, so a window [from, to] (spec 5) is drawn from..to.
 */
export function trackLines(series: Track, from = -Infinity, to = Infinity): Feature<MultiLineString> {
  const lines: Position[][] = []
  let current: Position[] = []
  series.t.forEach((t, index) => {
    if (t < from || t > to) return
    const position = positionOf(series, index)
    if (position) {
      current.push(position)
      if (current.length === MAX_LINE_POSITIONS) {
        lines.push(current)
        current = [position]
      }
    } else {
      if (current.length > 1) lines.push(current)
      current = []
    }
  })
  if (current.length > 1) lines.push(current)
  return { type: 'Feature', properties: {}, geometry: { type: 'MultiLineString', coordinates: lines } }
}

/** A point at the start of every effort with a position. */
export function effortStarts(series: Track, efforts: Effort[]): FeatureCollection<Point> {
  return {
    type: 'FeatureCollection',
    features: efforts.flatMap((effort) => {
      const position = positionAt(series, effort.startT)
      return position
        ? [{ type: 'Feature' as const, properties: { effortId: effort.id }, geometry: { type: 'Point' as const, coordinates: position } }]
        : []
    }),
  }
}

/** The cursor of the charts as a point, or nothing when it is off the charts or in a gap. */
export function cursorPoint(series: Track, t: number | null): FeatureCollection<Point> {
  const position = t == null ? null : positionAt(series, t)
  return {
    type: 'FeatureCollection',
    features: position ? [{ type: 'Feature', properties: {}, geometry: { type: 'Point', coordinates: position } }] : [],
  }
}

/** South-west and north-east corners of the track between `from` and `to`, or null without positions. */
export function trackBounds(series: Track, from = -Infinity, to = Infinity): [Position, Position] | null {
  let bounds: [Position, Position] | null = null
  series.t.forEach((t, index) => {
    const position = t >= from && t <= to ? positionOf(series, index) : null
    if (!position) return
    const [lon, lat] = position
    if (!bounds) bounds = [[lon, lat], [lon, lat]]
    else bounds = [[Math.min(bounds[0][0], lon), Math.min(bounds[0][1], lat)], [Math.max(bounds[1][0], lon), Math.max(bounds[1][1], lat)]]
  })
  return bounds
}

/** The outline of a geozone; a circle becomes a polygon of 64 vertices. */
export function geozoneOutline(shape: GeozoneShape): Feature<Polygon> | null {
  let rings: Position[][] | null = null
  if (shape.type === 'circle' && shape.lat != null && shape.lon != null && shape.radiusM != null) {
    const angle = shape.radiusM / EARTH_RADIUS_M * (180 / Math.PI)
    const ring = Array.from({ length: CIRCLE_VERTICES + 1 }, (_, i) => {
      const bearing = (2 * Math.PI * i) / CIRCLE_VERTICES
      return [
        shape.lon! + (angle * Math.sin(bearing)) / Math.cos((shape.lat! * Math.PI) / 180),
        shape.lat! + angle * Math.cos(bearing),
      ]
    })
    rings = [ring]
  } else if (shape.type === 'polygon' && shape.coordinates) {
    rings = shape.coordinates
  }
  return rings ? { type: 'Feature', properties: {}, geometry: { type: 'Polygon', coordinates: rings } } : null
}
