import 'maplibre-gl/dist/maplibre-gl.css'
import { type LngLatBoundsLike, Map, NavigationControl, setWorkerUrl } from 'maplibre-gl'
// MapLibre finds its worker next to its own module, which bundling moves; Vite builds the worker and gives its URL.
import workerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?worker&url'
import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import type { MapConfig } from '../../api/types'
import { type BaseMap, baseMapStyle } from './baseMap'

setWorkerUrl(workerUrl)

interface Props {
  config: MapConfig
  /** The area shown first. */
  bounds?: LngLatBoundsLike
  /** Adds the overlays: on load and again after every switch of the base map, which replaces the style. */
  onStyleLoad: (map: Map) => void
  /** The map once created, and null when it is gone; for updating overlay data. */
  onMap?: (map: Map | null) => void
}

/** A MapLibre map with the configured base maps and a switch between them (spec 10.2). */
export function MapView({ config, bounds, onStyleLoad, onMap }: Props) {
  const container = useRef<HTMLDivElement>(null)
  const map = useRef<Map | null>(null)
  const [base, setBase] = useState<BaseMap>('map')
  const latest = useRef({ onStyleLoad, onMap, bounds, base })

  useLayoutEffect(() => {
    latest.current = { onStyleLoad, onMap, bounds, base }
  })

  useEffect(() => {
    const instance = new Map({
      container: container.current!,
      style: baseMapStyle(config, latest.current.base),
      bounds: latest.current.bounds,
      fitBoundsOptions: { padding: 32, maxZoom: 18 },
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

  function show(next: BaseMap) {
    setBase(next)
    map.current?.setStyle(baseMapStyle(config, next), { diff: false })
  }

  return (
    <div className="map-view">
      <div ref={container} className="map-canvas" />
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
