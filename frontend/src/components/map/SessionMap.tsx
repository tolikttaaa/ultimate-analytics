import type { FeatureCollection } from 'geojson'
import type { GeoJSONSource, LayerSpecification, Map } from 'maplibre-gl'
import { useEffect, useLayoutEffect, useMemo, useRef } from 'react'
import type { Effort, Geozone, MapConfig, SessionSeries, Surface } from '../../api/types'
import type { Cursor } from '../../pages/session/cursor'
import type { TimeWindow } from '../../pages/session/timeWindow'
import { cursorPoint, effortStarts, geozoneOutline, trackBounds, trackLines } from './geo'
import { MapView } from './MapView'

interface Props {
  config: MapConfig
  series: SessionSeries
  efforts: Effort[]
  timeWindow: TimeWindow | null
  /** The geozone the session matched, outlined in its surface colour. */
  geozone: Geozone | null
  cursor: Cursor
}

/* As the surface chips. */
const SURFACE_COLORS: Record<Surface, string> = { GRASS: '#2e7d32', SAND: '#f59e0b', UNKNOWN: '#8c959f' }
const ACCENT = '#1565c0'
const EMPTY: FeatureCollection = { type: 'FeatureCollection', features: [] }

/**
 * The map of the session screen (spec 10.2): the whole track, the time window, effort starts, the charts' cursor
 * and the matched geozone, over the configured base map.
 */
export function SessionMap({ config, series, efforts, timeWindow, geozone, cursor }: Props) {
  const map = useRef<Map | null>(null)
  const bounds = useMemo(() => trackBounds(series), [series])
  const data = useMemo(() => ({
    track: trackLines(series),
    window: timeWindow ? trackLines(series, timeWindow[0], timeWindow[1]) : EMPTY,
    efforts: effortStarts(series, efforts),
    geozone: (geozone && geozoneOutline(geozone.shape)) ?? EMPTY,
  }), [series, efforts, timeWindow, geozone])
  const surfaceColor = SURFACE_COLORS[geozone?.surface ?? 'UNKNOWN']
  const latest = useRef({ data, surfaceColor })

  useLayoutEffect(() => {
    latest.current = { data, surfaceColor }
  })

  // Data changes go straight into the sources; the layers stay.
  useEffect(() => {
    for (const [id, value] of Object.entries(data)) source(map.current, id)?.setData(value)
    if (map.current?.getLayer('geozone-line')) {
      map.current.setPaintProperty('geozone-line', 'line-color', surfaceColor)
      map.current.setPaintProperty('geozone-fill', 'fill-color', surfaceColor)
    }
  }, [data, surfaceColor])

  useEffect(() => cursor.subscribe(() => source(map.current, 'cursor')?.setData(cursorPoint(series, cursor.get()))),
    [cursor, series])

  if (!bounds) return <div className="map-placeholder">No GPS positions in this session</div>

  function addOverlays(target: Map) {
    const { data: current, surfaceColor: color } = latest.current
    for (const [id, value] of Object.entries({ ...current, cursor: cursorPoint(series, cursor.get()) })) {
      target.addSource(id, { type: 'geojson', data: value })
    }
    for (const layer of overlayLayers(color)) target.addLayer(layer)
  }

  return (
    <MapView
      config={config}
      bounds={bounds as [[number, number], [number, number]]}
      onStyleLoad={addOverlays}
      onMap={(instance) => (map.current = instance)}
    />
  )
}

function source(map: Map | null, id: string): GeoJSONSource | undefined {
  return map?.getSource<GeoJSONSource>(id)
}

const ROUND = { 'line-join': 'round', 'line-cap': 'round' } as const

/** Bottom to top: geozone, track, window, effort starts, cursor. */
function overlayLayers(surfaceColor: string): LayerSpecification[] {
  return [
    { id: 'geozone-fill', type: 'fill', source: 'geozone', paint: { 'fill-color': surfaceColor, 'fill-opacity': 0.12 } },
    {
      id: 'geozone-line',
      type: 'line',
      source: 'geozone',
      paint: { 'line-color': surfaceColor, 'line-width': 2, 'line-dasharray': [2, 1.5] },
    },
    // A light casing keeps the track visible on dark imagery.
    { id: 'track-casing', type: 'line', source: 'track', layout: ROUND, paint: { 'line-color': '#ffffff', 'line-width': 4.5, 'line-opacity': 0.8 } },
    { id: 'track', type: 'line', source: 'track', layout: ROUND, paint: { 'line-color': '#455a64', 'line-width': 2 } },
    { id: 'window', type: 'line', source: 'window', layout: ROUND, paint: { 'line-color': ACCENT, 'line-width': 5 } },
    {
      id: 'efforts',
      type: 'circle',
      source: 'efforts',
      paint: { 'circle-radius': 4.5, 'circle-color': '#f57c00', 'circle-stroke-color': '#ffffff', 'circle-stroke-width': 1.5 },
    },
    {
      id: 'cursor',
      type: 'circle',
      source: 'cursor',
      paint: { 'circle-radius': 7, 'circle-color': ACCENT, 'circle-stroke-color': '#ffffff', 'circle-stroke-width': 2 },
    },
  ]
}
