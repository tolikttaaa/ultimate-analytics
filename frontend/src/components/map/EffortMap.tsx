import type { GeoJSONSource, Map } from 'maplibre-gl'
import { useEffect, useLayoutEffect, useMemo, useRef } from 'react'
import type { Effort, MapConfig, SessionSeries } from '../../api/types'
import { cursorPoint, trackBounds, trackLines } from './geo'
import { MapView } from './MapView'

interface Props {
  config: MapConfig
  series: SessionSeries
  effort: Effort
}

const FIT = { padding: 40, maxZoom: 19, duration: 0 }

/** The path of one effort, from its start (dot) to its end, in the effort drawer (spec 10.2). */
export function EffortMap({ config, series, effort }: Props) {
  const map = useRef<Map | null>(null)
  const data = useMemo(() => ({
    'effort-path': trackLines(series, effort.startT, effort.endT),
    'effort-start': cursorPoint(series, effort.startT),
  }), [series, effort])
  const bounds = useMemo(() => trackBounds(series, effort.startT, effort.endT), [series, effort])
  const latest = useRef(data)

  useLayoutEffect(() => {
    latest.current = data
  })

  // Next / previous effort: new data, same map.
  useEffect(() => {
    for (const [id, value] of Object.entries(data)) map.current?.getSource<GeoJSONSource>(id)?.setData(value)
    if (bounds) map.current?.fitBounds(bounds as [[number, number], [number, number]], FIT)
  }, [data, bounds])

  if (!bounds) return <div className="map-placeholder">No GPS positions for this effort</div>

  function addOverlays(target: Map) {
    for (const [id, value] of Object.entries(latest.current)) target.addSource(id, { type: 'geojson', data: value })
    target.addLayer({
      id: 'effort-path-casing',
      type: 'line',
      source: 'effort-path',
      layout: { 'line-join': 'round', 'line-cap': 'round' },
      paint: { 'line-color': '#ffffff', 'line-width': 7, 'line-opacity': 0.8 },
    })
    target.addLayer({
      id: 'effort-path',
      type: 'line',
      source: 'effort-path',
      layout: { 'line-join': 'round', 'line-cap': 'round' },
      paint: { 'line-color': '#f57c00', 'line-width': 4 },
    })
    target.addLayer({
      id: 'effort-start',
      type: 'circle',
      source: 'effort-start',
      paint: { 'circle-radius': 6, 'circle-color': '#ffffff', 'circle-stroke-color': '#f57c00', 'circle-stroke-width': 3 },
    })
  }

  return (
    <MapView
      config={config}
      bounds={bounds as [[number, number], [number, number]]}
      fitBoundsOptions={FIT}
      onStyleLoad={addOverlays}
      onMap={(instance) => (map.current = instance)}
    />
  )
}
