import type { Position } from 'geojson'
import type { ExpressionSpecification, GeoJSONSource, Map } from 'maplibre-gl'
import { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react'
import type { Geozone, MapConfig } from '../../api/types'
import { type Draft, draftFeatures, geozoneFeatures, geozonesBounds } from '../../pages/geozones/draft'
import { MapView } from './MapView'

interface Props {
  config: MapConfig
  geozones: Geozone[]
  selectedId: string | null
  /** A geozone to bring into view; each change moves the map. */
  focusId: string | null
  /** The shape being drawn; while there is one, clicks go to it. */
  draft: Draft | null
  /** Where the map starts without geozones, e.g. the latest session's start. */
  fallbackCenter: Position | null
  onMapClick: (position: Position) => void
  /** A double-click while drawing a polygon. */
  onFinish: () => void
  onSelect: (id: string | null) => void
}

/* As the surface chips. */
const SURFACE_COLOR: ExpressionSpecification = ['match', ['get', 'surface'], 'GRASS', '#2e7d32', 'SAND', '#f59e0b', '#8c959f']
const ACCENT = '#1565c0'
const FOCUS = { padding: 60, maxZoom: 18 }

/** The map of the geozones screen (spec 10.1): every geozone in its surface colour, and the shape being drawn. */
export function GeozoneMap(props: Props) {
  const { config, geozones, selectedId, focusId, draft, fallbackCenter } = props
  const map = useRef<Map | null>(null)
  const latest = useRef(props)
  const zones = useMemo(() => geozoneFeatures(geozones), [geozones])
  const drawing = useMemo(() => draftFeatures(draft), [draft])
  const [start] = useState(() => geozonesBounds(geozones))

  useLayoutEffect(() => {
    latest.current = props
  })

  useEffect(() => {
    source(map.current, 'geozones')?.setData(zones)
  }, [zones])
  useEffect(() => {
    source(map.current, 'draft')?.setData(drawing)
  }, [drawing])
  useEffect(() => {
    if (map.current?.getLayer('geozones-selected')) map.current.setFilter('geozones-selected', selectedFilter(selectedId))
  }, [selectedId])
  // Only a new focus moves the map, not edits of the focused geozone.
  useEffect(() => {
    const zone = latest.current.geozones.find((geozone) => geozone.id === focusId)
    const bounds = zone && geozonesBounds([zone])
    if (bounds) map.current?.fitBounds(bounds as [[number, number], [number, number]], FOCUS)
  }, [focusId])

  function addOverlays(target: Map) {
    target.addSource('geozones', { type: 'geojson', data: geozoneFeatures(latest.current.geozones) })
    target.addSource('draft', { type: 'geojson', data: draftFeatures(latest.current.draft) })
    target.addLayer({ id: 'geozones-fill', type: 'fill', source: 'geozones', paint: { 'fill-color': SURFACE_COLOR, 'fill-opacity': 0.2 } })
    target.addLayer({ id: 'geozones-line', type: 'line', source: 'geozones', paint: { 'line-color': SURFACE_COLOR, 'line-width': 2 } })
    target.addLayer({
      id: 'geozones-selected',
      type: 'line',
      source: 'geozones',
      filter: selectedFilter(latest.current.selectedId),
      paint: { 'line-color': ACCENT, 'line-width': 4 },
    })
    target.addLayer({
      id: 'draft-fill',
      type: 'fill',
      source: 'draft',
      filter: ['==', ['geometry-type'], 'Polygon'],
      paint: { 'fill-color': ACCENT, 'fill-opacity': 0.15 },
    })
    target.addLayer({
      id: 'draft-line',
      type: 'line',
      source: 'draft',
      filter: ['!=', ['geometry-type'], 'Point'],
      paint: { 'line-color': ACCENT, 'line-width': 2, 'line-dasharray': [2, 1] },
    })
    target.addLayer({
      id: 'draft-points',
      type: 'circle',
      source: 'draft',
      filter: ['==', ['geometry-type'], 'Point'],
      paint: { 'circle-radius': 5, 'circle-color': '#ffffff', 'circle-stroke-color': ACCENT, 'circle-stroke-width': 2 },
    })
  }

  function attach(instance: Map | null) {
    map.current = instance
    if (!instance) return
    instance.on('click', (event) => {
      const { draft: current, onMapClick, onSelect } = latest.current
      if (current) return onMapClick([event.lngLat.lng, event.lngLat.lat])
      const hit = instance.getLayer('geozones-fill') ? instance.queryRenderedFeatures(event.point, { layers: ['geozones-fill'] })[0] : undefined
      onSelect((hit?.properties?.id as string | undefined) ?? null)
    })
    instance.on('dblclick', (event) => {
      const current = latest.current.draft
      if (current?.kind === 'polygon' && !current.closed) {
        event.preventDefault()
        latest.current.onFinish()
      }
    })
    instance.on('mousemove', (event) => {
      const over = !latest.current.draft && instance.getLayer('geozones-fill') &&
        instance.queryRenderedFeatures(event.point, { layers: ['geozones-fill'] }).length > 0
      instance.getCanvas().style.cursor = latest.current.draft ? 'crosshair' : over ? 'pointer' : ''
    })
  }

  return (
    <MapView
      config={config}
      bounds={start ? (start as [[number, number], [number, number]]) : undefined}
      fitBoundsOptions={FOCUS}
      center={start ? undefined : ((fallbackCenter ?? [0, 20]) as [number, number])}
      zoom={start ? undefined : fallbackCenter ? 16 : 1.5}
      onStyleLoad={addOverlays}
      onMap={attach}
    />
  )
}

function source(map: Map | null, id: string): GeoJSONSource | undefined {
  return map?.getSource<GeoJSONSource>(id)
}

function selectedFilter(id: string | null): ExpressionSpecification {
  return ['==', ['get', 'id'], id ?? '']
}
