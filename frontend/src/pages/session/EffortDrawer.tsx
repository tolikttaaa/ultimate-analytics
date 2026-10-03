import { lazy, Suspense, useMemo } from 'react'
import type { Effort, MapConfig, SessionSeries } from '../../api/types'
import { EChart } from '../../components/charts/EChart'
import { effortChartOption } from '../../components/charts/sessionChartOptions'
import { useChartPalette } from '../../components/charts/useChartPalette'
import { duration, speedKmh } from '../../format'

const EffortMap = lazy(() => import('../../components/map/EffortMap').then((module) => ({ default: module.EffortMap })))

interface Props {
  effortId: string
  efforts: Effort[]
  series: SessionSeries
  mapConfig: MapConfig | undefined
  onSelect: (effortId: string) => void
  onClose: () => void
}

const GPS_NOTE = 'Derived from 1 Hz GPS speed: comparable between sessions recorded the same way, not a lab measurement.'
type Metrics = Effort['metrics']

const meters = (value: number | null | undefined) => (value == null ? '–' : `${Math.round(value)} m`)
const seconds = (value: number | null | undefined, decimals = 0) => (value == null ? '–' : `${value.toFixed(decimals)} s`)
const accel = (value: number) => `${value.toFixed(1)} m/s²`
const bpm = (value: number | null | undefined) => (value == null ? '–' : `${value} bpm`)

/** The per-effort metrics of spec 6.4, in display units. */
const ROWS: { label: string; value: (m: Metrics) => string; title?: string }[] = [
  { label: 'Duration', value: (m) => seconds(m.durationSec) },
  { label: 'Distance', value: (m) => meters(m.distanceM) },
  { label: 'Start speed', value: (m) => speedKmh(m.startSpeed) },
  { label: 'Peak speed', value: (m) => speedKmh(m.peakSpeed) },
  { label: 'Mean speed', value: (m) => speedKmh(m.meanSpeed) },
  { label: 'Time to peak', value: (m) => seconds(m.timeToPeakSec) },
  { label: 'Time to 80 % of peak', value: (m) => seconds(m.timeTo80PctPeakSec, 1) },
  { label: 'Speed after 1 s', value: (m) => speedKmh(m.speedAt1s) },
  { label: 'Speed after 2 s', value: (m) => speedKmh(m.speedAt2s) },
  { label: 'Speed after 3 s', value: (m) => speedKmh(m.speedAt3s) },
  { label: 'First 3 s speed', value: (m) => speedKmh(m.meanSpeedFirst3s) },
  { label: 'First 3 s distance', value: (m) => meters(m.distanceFirst3s) },
  { label: 'Mean acceleration (GPS)', value: (m) => accel(m.meanAccel), title: GPS_NOTE },
  { label: 'Peak acceleration (GPS)', value: (m) => accel(m.peakAccel), title: GPS_NOTE },
  { label: 'Max deceleration after (GPS)', value: (m) => accel(m.maxDecelAfter), title: GPS_NOTE },
  { label: 'Heart rate at start', value: (m) => bpm(m.hrStart) },
  { label: 'Max heart rate', value: (m) => bpm(m.hrMax) },
]

/**
 * The effort drawer (spec 10.2): a close-up chart from 3 s before to 3 s after the effort, its path on a mini map,
 * all its metrics, and the previous and next effort.
 */
export function EffortDrawer({ effortId, efforts, series, mapConfig, onSelect, onClose }: Props) {
  const sorted = useMemo(() => [...efforts].sort((a, b) => a.startT - b.startT), [efforts])
  const index = sorted.findIndex((effort) => effort.id === effortId)
  const effort = sorted[index]
  const palette = useChartPalette()
  const option = useMemo(() => (effort ? effortChartOption(series, effort, palette) : null), [series, effort, palette])
  if (!effort || !option) return null

  return (
    <aside className="drawer" role="dialog" aria-labelledby="effort-title">
      <div className="drawer-header">
        <h2 id="effort-title">Effort {index + 1} of {sorted.length}</h2>
        <span className="muted">{duration(effort.startT)}–{duration(effort.endT)}</span>
        <span className="drawer-actions">
          <button className="button small" disabled={index === 0} onClick={() => onSelect(sorted[index - 1].id)}>
            Previous
          </button>
          <button className="button small" disabled={index === sorted.length - 1} onClick={() => onSelect(sorted[index + 1].id)}>
            Next
          </button>
          <button className="button link" onClick={onClose} aria-label="Close" title="Escape">✕</button>
        </span>
      </div>
      <EChart option={option} height={210} />
      <div className="effort-map">
        {mapConfig ? (
          <Suspense fallback={<div className="map-placeholder">Loading map…</div>}>
            <EffortMap config={mapConfig} series={series} effort={effort} />
          </Suspense>
        ) : (
          <div className="map-placeholder">Loading map…</div>
        )}
      </div>
      <table className="metrics-table" aria-label="Effort metrics">
        <tbody>
          {ROWS.map((row) => (
            <tr key={row.label} title={row.title}>
              <td>{row.label}</td>
              <td className="number">{row.value(effort.metrics)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </aside>
  )
}
