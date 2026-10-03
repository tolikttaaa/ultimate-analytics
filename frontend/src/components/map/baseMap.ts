import type { StyleSpecification } from 'maplibre-gl'
import type { MapConfig } from '../../api/types'
import type { Theme } from '../../theme'

export type BaseMap = 'map' | 'satellite'

/**
 * The MapLibre style of a base map (spec 10.2): the vector style of the theme by URL, or a raster style around the
 * configured satellite tiles. Providers and keys come from `GET /api/config`.
 */
export function baseMapStyle(config: MapConfig, base: BaseMap, theme: Theme = 'light'): string | StyleSpecification {
  const satellite = config.satellite
  if (base === 'map' || !satellite) return theme === 'dark' ? config.vectorStyleUrlDark : config.vectorStyleUrl
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
