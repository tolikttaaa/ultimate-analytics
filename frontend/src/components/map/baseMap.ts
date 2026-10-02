import type { StyleSpecification } from 'maplibre-gl'
import type { MapConfig } from '../../api/types'

export type BaseMap = 'map' | 'satellite'

/**
 * The MapLibre style of a base map (spec 10.2): the vector style by URL, or a raster style around the configured
 * satellite tiles. Providers and keys come from `GET /api/config`.
 */
export function baseMapStyle(config: MapConfig, base: BaseMap): string | StyleSpecification {
  const satellite = config.satellite
  if (base === 'map' || !satellite) return config.vectorStyleUrl
  return {
    version: 8,
    sources: {
      satellite: {
        type: 'raster',
        tiles: [satellite.tilesUrl],
        tileSize: satellite.tileSize,
        maxzoom: satellite.maxZoom,
        attribution: satellite.attribution,
      },
    },
    layers: [{ id: 'satellite', type: 'raster', source: 'satellite' }],
  }
}
