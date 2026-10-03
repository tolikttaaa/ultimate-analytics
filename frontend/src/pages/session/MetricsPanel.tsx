import type { MeanAndBest, WindowMetrics } from '../../api/types'
import { distance, duration, pace, speedKmh } from '../../format'

/** One column of the panel: the metrics of the session, of a window, ...; without metrics yet it shows "…". */
export interface MetricsColumn {
  label: string
  metrics?: WindowMetrics
}

const PENDING = '…'

const GPS_NOTE = 'Derived from 1 Hz GPS speed: comparable between sessions recorded the same way, not a lab measurement.'
const MISSING = '–'

const meanBest = (stat: MeanAndBest | null | undefined, format: (value: number) => string) =>
  stat ? `${format(stat.mean)} / ${format(stat.best)}` : MISSING
const seconds = (value: number) => `${value.toFixed(1)} s`
const accel = (value: number) => `${value.toFixed(1)} m/s²`

interface Row {
  label: string
  value: (metrics: WindowMetrics) => string
  title?: string
}

/** The metrics of spec 6.5, in display units (spec 10.3). */
const GROUPS: { title: string; rows: Row[] }[] = [
  {
    title: 'Time',
    rows: [
      { label: 'Elapsed', value: (m) => duration(m.time.elapsedSec) },
      { label: 'Active', value: (m) => duration(m.time.activeSec) },
      { label: 'Paused', value: (m) => duration(m.time.pausedSec) },
      { label: 'Gaps', value: (m) => duration(m.time.gapSec) },
      { label: 'Work : rest', value: (m) => m.time.workRestRatio?.toFixed(2) ?? MISSING },
    ],
  },
  {
    title: 'Distance and speed',
    rows: [
      { label: 'Distance', value: (m) => distance(m.distance.distanceM) },
      { label: 'Active distance', value: (m) => distance(m.distance.activeDistanceM) },
      { label: 'Moving speed', value: (m) => speedKmh(m.distance.movingSpeed), title: 'Mean speed without pauses and walking' },
      { label: 'Moving pace', value: (m) => pace(m.distance.movingPaceSecPerKm) },
      { label: 'Max speed', value: (m) => speedKmh(m.distance.maxSpeed) },
    ],
  },
  {
    title: 'Efforts (mean / best)',
    rows: [
      { label: 'Efforts', value: (m) => String(m.efforts.count) },
      { label: 'Per active minute', value: (m) => m.efforts.perActiveMin?.toFixed(2) ?? MISSING },
      { label: 'Peak speed', value: (m) => meanBest(m.efforts.peakSpeed, speedKmh) },
      { label: 'Mean speed', value: (m) => meanBest(m.efforts.meanSpeed, speedKmh) },
      { label: 'First 3 s speed', value: (m) => meanBest(m.efforts.meanSpeedFirst3s, speedKmh) },
      { label: 'Time to 80 % of peak', value: (m) => meanBest(m.efforts.timeTo80PctPeakSec, seconds) },
      { label: 'Mean acceleration (GPS)', value: (m) => meanBest(m.efforts.meanAccel, accel), title: GPS_NOTE },
      { label: 'Peak acceleration (GPS)', value: (m) => meanBest(m.efforts.peakAccel, accel), title: GPS_NOTE },
      { label: 'Decelerations (GPS)', value: (m) => String(m.decelCount), title: GPS_NOTE },
      {
        label: 'Peak speed drop',
        value: (m) => (m.fatigue.peakSpeedDropPct == null ? MISSING : `${m.fatigue.peakSpeedDropPct.toFixed(1)} %`),
        title: 'First third of the efforts against the last third; needs at least 6 efforts',
      },
    ],
  },
  {
    title: 'Heart rate',
    rows: [
      { label: 'Average', value: (m) => (m.heartRate.avg == null ? MISSING : `${Math.round(m.heartRate.avg)} bpm`) },
      { label: 'Max', value: (m) => (m.heartRate.max == null ? MISSING : `${m.heartRate.max} bpm`) },
      ...[1, 2, 3, 4, 5].map((zone) => ({
        label: `Zone ${zone}`,
        value: (m: WindowMetrics) => {
          const share = m.heartRate.zonesPct[zone - 1]
          return share == null ? MISSING : `${Math.round(share)} %`
        },
        title: 'Share of the time with heart rate, zones from 50 / 60 / 70 / 80 / 90 % of max HR',
      })),
    ],
  },
]

const ZONE_LABELS: Record<string, string> = {
  STAND: 'Stand', WALK: 'Walk', JOG: 'Jog', RUN: 'Run', HIGH_SPEED: 'High speed', SPRINT: 'Sprint',
}

/** Metrics of the session and of the selected window side by side (spec 10.2). */
export function MetricsPanel({ columns }: { columns: MetricsColumn[] }) {
  return (
    <section className="metrics-panel" aria-label="Metrics">
      <table className="metrics-table">
        <thead>
          <tr>
            <th />
            {columns.map((column) => <th key={column.label} className="number">{column.label}</th>)}
          </tr>
        </thead>
        {GROUPS.map((group) => (
          <tbody key={group.title}>
            <tr className="group">
              <th colSpan={columns.length + 1}>{group.title}</th>
            </tr>
            {group.rows.map((row) => (
              <tr key={row.label} title={row.title}>
                <td>{row.label}</td>
                {columns.map((column) => (
                  <td key={column.label} className="number">{column.metrics ? row.value(column.metrics) : PENDING}</td>
                ))}
              </tr>
            ))}
          </tbody>
        ))}
        <tbody>
          <tr className="group">
            <th colSpan={columns.length + 1}>Speed zones: time and distance</th>
          </tr>
          {(columns[0].metrics?.zones ?? []).map((zone, index) => (
            <tr key={zone.zone}>
              <td>{ZONE_LABELS[zone.zone] ?? zone.zone}</td>
              {columns.map((column) => {
                const own = column.metrics?.zones[index]
                return (
                  <td key={column.label} className="number">
                    {own ? `${duration(own.timeSec)}, ${distance(own.distanceM)}` : PENDING}
                  </td>
                )
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  )
}
