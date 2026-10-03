import 'maplibre-gl/dist/maplibre-gl.css'
import { type FitBoundsOptions, type LngLatBoundsLike, type LngLatLike, Map, NavigationControl, setWorkerUrl } from 'maplibre-gl'
// MapLibre finds its worker next to its own module, which bundling moves; Vite builds the worker and gives its URL.
import workerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?worker&url'
import { type ReactNode, useEffect, useLayoutEffect, useRef, useState } from 'react'
import type { MapConfig } from '../../api/types'
import { useTheme } from '../../theme'
import { type BaseMap, baseMapStyle } from './baseMap'

setWorkerUrl(workerUrl)

interface Props {
  config: MapConfig
  /** The area shown first; without it, `center` and `zoom`. */
  bounds?: LngLatBoundsLike
  fitBoundsOptions?: FitBoundsOptions
  center?: LngLatLike
  zoom?: number
  /** Adds the overlays: on load and again after every switch of the base map, which replaces the style. */
  onStyleLoad: (map: Map) => void
  /** The map once created, and null when it is gone; for updating overlay data. */
  onMap?: (map: Map | null) => void
  /** Controls of the overlays, shown at the top left of the map, next to the zoom buttons. */
  controls?: ReactNode
}

/** A MapLibre map with the configured base maps and a switch between them (spec 10.2). */
export function MapView(props: Props) {
  const { config, bounds, fitBoundsOptions = { padding: 32, maxZoom: 18 }, center, zoom, onStyleLoad, onMap, controls } = props
  const container = useRef<HTMLDivElement>(null)
  const map = useRef<Map | null>(null)
  const [base, setBase] = useState<BaseMap>('map')
  const { theme } = useTheme()
  const latest = useRef({ onStyleLoad, onMap, bounds, fitBoundsOptions, center, zoom, base, theme })

  useLayoutEffect(() => {
    latest.current = { onStyleLoad, onMap, bounds, fitBoundsOptions, center, zoom, base, theme }
  })

  useEffect(() => {
    const instance = new Map({
      container: container.current!,
      style: baseMapStyle(config, latest.current.base, latest.current.theme),
      bounds: latest.current.bounds,
      fitBoundsOptions: latest.current.fitBoundsOptions,
      center: latest.current.center,
      zoom: latest.current.zoom,
      attributionControl: { compact: true },
    })
    instance.addControl(new NavigationControl({ showCompass: false }), 'top-left')
    instance.on('style.load', () => latest.current.onStyleLoad(instance))
    map.current = instance
    latest.current.onMap?.(instance)
    return () => {
      latest.current.onMap?.(null)
      instance.remove()
      map.current = null
    }
  }, [config])

  // A new theme brings its base map; the overlays come back with `style.load`.
  useEffect(() => {
    map.current?.setStyle(baseMapStyle(config, latest.current.base, theme), { diff: false })
  }, [config, theme])

  function show(next: BaseMap) {
    setBase(next)
    map.current?.setStyle(baseMapStyle(config, next, theme), { diff: false })
  }

  return (
    <div className="map-view">
      <div ref={container} className="map-canvas" />
      {controls && <div className="map-controls">{controls}</div>}
      {config.satellite && (
        <div className="basemap-switch" role="group" aria-label="Base map">
          {(['map', 'satellite'] as const).map((value) => (
            <button key={value} aria-pressed={value === base} onClick={() => show(value)}>
              {value === 'map' ? 'Map' : 'Satellite'}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
