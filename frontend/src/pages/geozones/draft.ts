import type { Feature, FeatureCollection, Polygon, Position } from 'geojson'
import type { Geozone, GeozoneShape } from '../../api/types'
import { geozoneOutline, SHAPE } from '../../components/map/geo'

/*
 * The shape being drawn on the geozones screen (spec 10.1): a circle is a click for the centre plus a radius, a
 * polygon is a click per corner until it is closed. Positions are [lon, lat].
 */

export type Draft =
  | { kind: 'circle'; center: Position | null; radiusM: number }
  | { kind: 'polygon'; points: Position[]; closed: boolean }

export const DEFAULT_RADIUS_M = 150
export const MIN_POLYGON_CORNERS = 3

/** Adds a map click: moves the circle's centre, or adds a corner to an open polygon. */
export function addPoint(draft: Draft, position: Position): Draft {
  if (draft.kind === 'circle') return { ...draft, center: position }
  return draft.closed ? draft : { ...draft, points: [...draft.points, position] }
}

/** Removes the last corner of an open polygon. */
export function undoPoint(draft: Draft): Draft {
  return draft.kind === 'polygon' && !draft.closed ? { ...draft, points: draft.points.slice(0, -1) } : draft
}

/**
 * Closes a polygon with enough corners. A double-click to finish also clicks twice, so repeated corners are dropped
 * first.
 */
export function finishPolygon(draft: Draft): Draft {
  if (draft.kind !== 'polygon') return draft
  const points = draft.points.filter((point, i, all) =>
    i === 0 || Math.abs(point[0] - all[i - 1][0]) > 1e-7 || Math.abs(point[1] - all[i - 1][1]) > 1e-7)
  return { ...draft, points, closed: points.length >= MIN_POLYGON_CORNERS }
}

/** The shape to save, or null while the draft is incomplete. */
export function draftShape(draft: Draft): GeozoneShape | null {
  if (draft.kind === 'circle') {
    if (!draft.center || !(draft.radiusM > 0)) return null
    return { type: SHAPE.circle, lon: draft.center[0], lat: draft.center[1], radiusM: draft.radiusM }
  }
  if (!draft.closed || draft.points.length < MIN_POLYGON_CORNERS) return null
  return { type: SHAPE.polygon, coordinates: [[...draft.points, draft.points[0]]] }
}

/** A draft of an existing shape, for editing it. */
export function shapeDraft(shape: GeozoneShape): Draft {
  if (shape.type === SHAPE.circle && shape.lat != null && shape.lon != null) {
    return { kind: 'circle', center: [shape.lon, shape.lat], radiusM: shape.radiusM ?? DEFAULT_RADIUS_M }
  }
  const ring = shape.coordinates?.[0] ?? []
  const open = ring.length > 1 && ring[0][0] === ring[ring.length - 1][0] && ring[0][1] === ring[ring.length - 1][1]
    ? ring.slice(0, -1)
    : ring
  return { kind: 'polygon', points: open, closed: true }
}

/** What the map shows of a draft: the area once it is a shape, else the corners so far and the line through them. */
export function draftFeatures(draft: Draft | null): FeatureCollection {
  if (!draft) return { type: 'FeatureCollection', features: [] }
  const shape = draftShape(draft)
  const area = shape ? geozoneOutline(shape) : null
  const corners: Position[] = draft.kind === 'circle' ? (draft.center ? [draft.center] : []) : draft.points
  const features: Feature[] = corners.map((position) => ({
    type: 'Feature',
    properties: {},
    geometry: { type: 'Point', coordinates: position },
  }))
  if (area) features.unshift(area)
  else if (draft.kind === 'polygon' && draft.points.length > 1) {
    features.unshift({ type: 'Feature', properties: {}, geometry: { type: 'LineString', coordinates: draft.points } })
  }
  return { type: 'FeatureCollection', features }
}

/** All geozones as areas carrying id, name and surface. */
export function geozoneFeatures(geozones: Geozone[]): FeatureCollection<Polygon> {
  return {
    type: 'FeatureCollection',
    features: geozones.flatMap((geozone) => {
      const outline = geozoneOutline(geozone.shape)
      return outline ? [{ ...outline, properties: { id: geozone.id, name: geozone.name, surface: geozone.surface } }] : []
    }),
  }
}

/** South-west and north-east corners around geozones, or null without any. */
export function geozonesBounds(geozones: Geozone[]): [Position, Position] | null {
  const positions = geozoneFeatures(geozones).features.flatMap((feature) => feature.geometry.coordinates[0])
  if (positions.length === 0) return null
  const lons = positions.map(([lon]) => lon)
  const lats = positions.map(([, lat]) => lat)
  return [[Math.min(...lons), Math.min(...lats)], [Math.max(...lons), Math.max(...lats)]]
}

/** A short description of a shape for the list, e.g. `Circle, 150 m` or `Polygon, 5 corners`. */
export function describeShape(shape: GeozoneShape): string {
  if (shape.type === SHAPE.circle) return `Circle, ${Math.round(shape.radiusM ?? 0)} m`
  const ring = shape.coordinates?.[0] ?? []
  return `Polygon, ${Math.max(0, ring.length - 1)} corners`
}
