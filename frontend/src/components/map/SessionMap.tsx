import type { FeatureCollection } from 'geojson'
import type { GeoJSONSource, LayerSpecification, Map } from 'maplibre-gl'
import { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react'
import type { DrillType, Effort, Geozone, MapConfig, Segment, SessionSeries, Surface } from '../../api/types'
import type { Cursor } from '../../pages/session/cursor'
import type { TimeWindow } from '../../pages/session/timeWindow'
import { type Theme, useTheme } from '../../theme'
import { segmentColor } from '../charts/sessionChartOptions'
import { cursorPoint, effortStarts, geozoneOutline, segmentTracks, trackBounds, trackLines } from './geo'
import { MapView } from './MapView'

interface Props {
  config: MapConfig
  series: SessionSeries
  efforts: Effort[]
  /** The track of each segment is drawn in its drill type's colour. */
  segments: Segment[]
  drillTypes: DrillType[]
  timeWindow: TimeWindow | null
  /** The geozone the session matched, outlined in its surface colour. */
  geozone: Geozone | null
  cursor: Cursor
}

/* As the surface chips. */
const SURFACE_COLORS: Record<Surface, string> = { GRASS: '#2e7d32', SAND: '#f59e0b', UNKNOWN: '#8c959f' }
/** Overlay colours that depend on the base map's theme. */
const TRACK_COLORS: Record<Theme, { track: string; casing: string; casingOpacity: number; accent: string }> = {
  light: { track: '#59636e', casing: '#ffffff', casingOpacity: 0.75, accent: '#1565c0' },
  dark: { track: '#aab7af', casing: '#0e1411', casingOpacity: 0.6, accent: '#6ea4ff' },
}
const EMPTY: FeatureCollection = { type: 'FeatureCollection', features: [] }
const FIT = { padding: 32, maxZoom: 18, duration: 400 }

/**
 * The map of the session screen (spec 10.2): the track, coloured by drill type where segments have one, the time
 * window as a halo (or alone), effort starts, the charts' cursor and the matched geozone, over the configured base
 * map.
 */
export function SessionMap({ config, series, efforts, segments, drillTypes, timeWindow, geozone, cursor }: Props) {
  const map = useRef<Map | null>(null)
  const [windowOnly, setWindowOnly] = useState(false)
  const bounds = useMemo(() => trackBounds(series), [series])
  // Only the window is drawn when the user asks for it and there is one.
  const [from, to] = windowOnly && timeWindow ? timeWindow : [-Infinity, Infinity]
  const data = useMemo(() => ({
    track: trackLines(series, from, to),
    segments: segmentTracks(series, segments, (segment) => segmentColor(segment, drillTypes), from, to),
    window: timeWindow ? trackLines(series, timeWindow[0], timeWindow[1]) : EMPTY,
    efforts: effortStarts(series, efforts.filter((effort) => effort.startT >= from && effort.startT <= to)),
    geozone: (geozone && geozoneOutline(geozone.shape)) ?? EMPTY,
  }), [series, segments, drillTypes, efforts, timeWindow, geozone, from, to])
  const surfaceColor = SURFACE_COLORS[geozone?.surface ?? 'UNKNOWN']
  const { theme } = useTheme()
  const latest = useRef({ data, surfaceColor, theme })

  useLayoutEffect(() => {
    latest.current = { data, surfaceColor, theme }
  })

  // Data changes go straight into the sources; the layers stay.
  useEffect(() => {
    for (const [id, value] of Object.entries(data)) source(map.current, id)?.setData(value)
    if (map.current?.getLayer('geozone-line')) {
      map.current.setPaintProperty('geozone-line', 'line-color', surfaceColor)
      map.current.setPaintProperty('geozone-fill', 'fill-color', surfaceColor)
    }
  }, [data, surfaceColor])

  // Switching between the whole track and the window shows what is drawn.
  useEffect(() => {
    const shown = Number.isFinite(from) ? trackBounds(series, from, to) : trackBounds(series)
    if (shown) map.current?.fitBounds(shown as [[number, number], [number, number]], FIT)
  }, [series, from, to])

  useEffect(() => cursor.subscribe(() => source(map.current, 'cursor')?.setData(cursorPoint(series, cursor.get()))),
    [cursor, series])

  if (!bounds) return <div className="map-placeholder">No GPS positions in this session</div>

  function addOverlays(target: Map) {
    const { data: current, surfaceColor: color, theme: mapTheme } = latest.current
    for (const [id, value] of Object.entries({ ...current, cursor: cursorPoint(series, cursor.get()) })) {
      target.addSource(id, { type: 'geojson', data: value })
    }
    for (const layer of overlayLayers(color, mapTheme)) target.addLayer(layer)
  }

  return (
    <MapView
      config={config}
      bounds={bounds as [[number, number], [number, number]]}
      onStyleLoad={addOverlays}
      onMap={(instance) => (map.current = instance)}
      controls={timeWindow && (
        <div className="map-toggle" role="group" aria-label="Track shown">
          <button aria-pressed={!windowOnly} onClick={() => setWindowOnly(false)}>Whole track</button>
          <button aria-pressed={windowOnly} onClick={() => setWindowOnly(true)}>Window only</button>
        </div>
      )}
    />
  )
}

function source(map: Map | null, id: string): GeoJSONSource | undefined {
  return map?.getSource<GeoJSONSource>(id)
}

const ROUND = { 'line-join': 'round', 'line-cap': 'round' } as const

/** Bottom to top: geozone, window halo, track casing, track, drill-coloured segments, effort starts, cursor. */
function overlayLayers(surfaceColor: string, theme: Theme): LayerSpecification[] {
  const { track, casing, casingOpacity, accent } = TRACK_COLORS[theme]
  return [
    { id: 'geozone-fill', type: 'fill', source: 'geozone', paint: { 'fill-color': surfaceColor, 'fill-opacity': 0.12 } },
    {
      id: 'geozone-line',
      type: 'line',
      source: 'geozone',
      paint: { 'line-color': surfaceColor, 'line-width': 1.5, 'line-dasharray': [2, 1.5] },
    },
    // A halo under the track marks the window without hiding the drill colours.
    { id: 'window', type: 'line', source: 'window', layout: ROUND, paint: { 'line-color': accent, 'line-width': 9, 'line-opacity': 0.35 } },
    // A light casing keeps the track visible on dark imagery.
    { id: 'track-casing', type: 'line', source: 'track', layout: ROUND, paint: { 'line-color': casing, 'line-width': 3, 'line-opacity': casingOpacity } },
    { id: 'track', type: 'line', source: 'track', layout: ROUND, paint: { 'line-color': track, 'line-width': 1.5 } },
    { id: 'segments', type: 'line', source: 'segments', layout: ROUND, paint: { 'line-color': ['get', 'color'], 'line-width': 2 } },
    {
      id: 'efforts',
      type: 'circle',
      source: 'efforts',
      // Rings, not dots: they stay visible on tracks of any drill colour.
      paint: { 'circle-radius': 3.5, 'circle-color': '#ffffff', 'circle-stroke-color': '#f57c00', 'circle-stroke-width': 2 },
    },
    {
      id: 'cursor',
      type: 'circle',
      source: 'cursor',
      paint: { 'circle-radius': 6, 'circle-color': accent, 'circle-stroke-color': '#ffffff', 'circle-stroke-width': 2 },
    },
  ]
}
