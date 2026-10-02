import type { Surface, SurfaceSource } from '../api/types'

const SURFACE_LABELS: Record<Surface, string> = { GRASS: 'Grass', SAND: 'Sand', UNKNOWN: 'Unknown' }

const SOURCE_TITLES: Record<SurfaceSource, string> = {
  GEOZONE: 'From the geozone of the session',
  MANUAL: 'Set by hand',
  NONE: 'No geozone matches the session',
}

/** Surface chip: grass green, sand amber, unknown grey (spec 10.3). */
export function SurfaceChip({ surface, source }: { surface: Surface; source?: SurfaceSource }) {
  return (
    <span className={`chip surface-${surface.toLowerCase()}`} title={source && SOURCE_TITLES[source]}>
      {SURFACE_LABELS[surface]}
    </span>
  )
}

/** A small label with an explanation on hover. */
export function Badge({ children, title, tone = 'neutral' }: { children: string; title: string; tone?: 'neutral' | 'warning' }) {
  return <span className={`badge badge-${tone}`} title={title}>{children}</span>
}
