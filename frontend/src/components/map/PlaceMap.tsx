import type { FeatureCollection, Position } from 'geojson'
import type { GeoJSONSource, Map } from 'maplibre-gl'
import { useEffect, useLayoutEffect, useMemo, useRef } from 'react'
import type { Geozone, MapConfig } from '../../api/types'
import { geozoneFeatures } from '../../pages/geozones/draft'
import { ACCENT, SURFACE_COLOR } from './colors'
import { geozoneOutline, SHAPE } from './geo'
import { MapView } from './MapView'

interface Props {
  config: MapConfig
  /** Where the session started, [lon, lat]. */
  position: Position
  /** Existing geozones, to see whether one is near. */
  geozones: Geozone[]
  /** Radius of a geozone about to be created around the start, drawn as a preview. */
  previewRadiusM: number | null
}

const EMPTY: FeatureCollection = { type: 'FeatureCollection', features: [] }

/** Where a session started, with the geozones around it, for picking its surface (backlog: map in the upload dialog). */
export function PlaceMap({ config, position, geozones, previewRadiusM }: Props) {
  const map = useRef<Map | null>(null)
  const data = useMemo((): Record<string, FeatureCollection> => {
    const preview = previewRadiusM != null && previewRadiusM > 0
      ? geozoneOutline({ type: SHAPE.circle, lon: position[0], lat: position[1], radiusM: previewRadiusM })
      : null
    return {
      'place-geozones': geozoneFeatures(geozones),
      'place-preview': preview ? { type: 'FeatureCollection', features: [preview] } : EMPTY,
      'place-start': { type: 'FeatureCollection', features: [{ type: 'Feature', properties: {}, geometry: { type: 'Point', coordinates: position } }] },
    }
  }, [position, geozones, previewRadiusM])
  const latest = useRef(data)

  useLayoutEffect(() => {
    latest.current = data
  })

  useEffect(() => {
    for (const [id, value] of Object.entries(data)) map.current?.getSource<GeoJSONSource>(id)?.setData(value)
  }, [data])

  // Another session: the map moves to its start.
  useEffect(() => {
    map.current?.jumpTo({ center: position as [number, number] })
  }, [position])

  function addOverlays(target: Map) {
    for (const [id, value] of Object.entries(latest.current)) target.addSource(id, { type: 'geojson', data: value })
    target.addLayer({ id: 'place-geozones-fill', type: 'fill', source: 'place-geozones', paint: { 'fill-color': SURFACE_COLOR, 'fill-opacity': 0.2 } })
    target.addLayer({ id: 'place-geozones-line', type: 'line', source: 'place-geozones', paint: { 'line-color': SURFACE_COLOR, 'line-width': 1.5 } })
    target.addLayer({ id: 'place-preview-fill', type: 'fill', source: 'place-preview', paint: { 'fill-color': ACCENT, 'fill-opacity': 0.12 } })
    target.addLayer({
      id: 'place-preview-line',
      type: 'line',
      source: 'place-preview',
      paint: { 'line-color': ACCENT, 'line-width': 1.5, 'line-dasharray': [2, 1] },
    })
    target.addLayer({
      id: 'place-start',
      type: 'circle',
      source: 'place-start',
      paint: { 'circle-radius': 6, 'circle-color': ACCENT, 'circle-stroke-color': '#ffffff', 'circle-stroke-width': 2 },
    })
  }

  return (
    <MapView
      config={config}
      center={position as [number, number]}
      zoom={15.5}
      onStyleLoad={addOverlays}
      onMap={(instance) => (map.current = instance)}
    />
  )
}
