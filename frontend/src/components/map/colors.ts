import type { ExpressionSpecification } from 'maplibre-gl'
import type { Surface } from '../../api/types'

/** Surface colours of the map overlays, as the surface chips. */
export const SURFACE_COLORS: Record<Surface, string> = { GRASS: '#2e7d32', SAND: '#f59e0b', UNKNOWN: '#8c959f' }

/** The surface colour of a feature carrying a `surface` property. */
export const SURFACE_COLOR: ExpressionSpecification = [
  'match', ['get', 'surface'], 'GRASS', SURFACE_COLORS.GRASS, 'SAND', SURFACE_COLORS.SAND, SURFACE_COLORS.UNKNOWN,
]

/** Overlays the user works with: the selected geozone, the shape being drawn, the start of a session. */
export const ACCENT = '#1565c0'
