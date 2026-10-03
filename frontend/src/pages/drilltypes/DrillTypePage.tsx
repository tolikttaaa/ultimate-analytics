import { useMemo, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
import { ApiError } from '../../api/client'
import { type DrillTypeStatsFilter, useDrillTypeStats } from '../../api/drillTypes'
import type { Surface } from '../../api/types'
import { SurfaceChip } from '../../components/Chips'
import { MetricValue } from '../../components/MetricValue'
import { EChart } from '../../components/charts/EChart'
import { TREND_METRICS, trendChartOption } from '../../components/charts/drillTypeCharts'
import { useChartPalette } from '../../components/charts/useChartPalette'
import { endOfDay, startOfDay } from '../../dates'
import { duration, speedKmh } from '../../format'
import { MetricsPanel } from '../session/MetricsPanel'
import { KIND_LABELS } from './drillTypeFormat'

const SURFACES: Surface[] = ['GRASS', 'SAND', 'UNKNOWN']

/**
 * Screen 5, detail (spec 10.1): all segments of one drill type across sessions, filtered by surface and date range:
 * totals (spec 6.5), a per-session trend and the sessions.
 */
export function DrillTypePage() {
  const { id = '' } = useParams()
  const [params, setParams] = useSearchParams()
  const [metricKey, setMetricKey] = useState(TREND_METRICS[0].key)
  const surface = params.get('surface') as Surface | null
  const fromDay = params.get('from') ?? ''
  const toDay = params.get('to') ?? ''
  const filter: DrillTypeStatsFilter = {
    surface: surface ?? undefined,
    from: fromDay ? startOfDay(fromDay) : undefined,
    to: toDay ? endOfDay(toDay) : undefined,
  }
  const stats = useDrillTypeStats(id, filter)
  const metric = TREND_METRICS.find((candidate) => candidate.key === metricKey) ?? TREND_METRICS[0]
  const sessions = stats.data?.sessions
  const color = stats.data?.drillType.color ?? '#59636e'
  const palette = useChartPalette()
  const option = useMemo(() => (sessions ? trendChartOption(sessions, metric, color, palette) : null), [sessions, metric, color, palette])

  function update(key: string, value: string | null) {
    const next = new URLSearchParams(params)
    if (value) next.set(key, value)
    else next.delete(key)
    setParams(next, { replace: true })
  }

  if (stats.isError && stats.error instanceof ApiError && stats.error.status === 404) {
    return <p role="alert">This drill type does not exist. <Link to="/drill-types">All drill types</Link></p>
  }

  const type = stats.data?.drillType
  const totals = stats.data?.totals
  const segmentCount = sessions?.reduce((sum, row) => sum + row.segmentCount, 0) ?? 0

  return (
    <section className="drill-type-page">
      <p className="breadcrumb"><Link to="/drill-types">Drill types</Link></p>
      <div className="page-header">
        <h1>
          {type && <span className="swatch large" style={{ background: type.color }} />}
          {type?.name ?? 'Drill type'}
        </h1>
        {type && <span className="muted">{KIND_LABELS[type.kind]}, code {type.code}</span>}
      </div>

      <div className="filters">
        <label>
          Surface
          <select value={surface ?? ''} onChange={(e) => update('surface', e.target.value || null)}>
            <option value="">All</option>
            {SURFACES.map((s) => <option key={s} value={s}>{s.charAt(0) + s.slice(1).toLowerCase()}</option>)}
          </select>
        </label>
        <label>
          From
          <input type="date" value={fromDay} onChange={(e) => update('from', e.target.value || null)} />
        </label>
        <label>
          To
          <input type="date" value={toDay} onChange={(e) => update('to', e.target.value || null)} />
        </label>
      </div>

      {stats.isPending && <p>Loading statistics…</p>}
      {stats.isError && !(stats.error instanceof ApiError && stats.error.status === 404) && (
        <p role="alert">Could not load the statistics: {stats.error.message}</p>
      )}
      {sessions && sessions.length === 0 && (
        <div className="empty">
          No segments of this type{surface || fromDay || toDay ? ' match the filters' : ' yet'}. Mark windows of your
          sessions as this drill type on the session screen.
        </div>
      )}
      {sessions && sessions.length > 0 && totals && option && (
        <div className="drill-type-body">
          <div className="drill-type-main">
            <dl className="key-metrics">
              <div><dt>Sessions</dt><dd>{sessions.length}</dd></div>
              <div><dt>Segments</dt><dd>{segmentCount}</dd></div>
              <div><dt>Time</dt><dd>{duration(totals.time.elapsedSec)}</dd></div>
              <div><dt>Efforts</dt><dd>{totals.efforts.count}</dd></div>
              <div><dt>Best peak speed</dt><dd><MetricValue value={speedKmh(totals.efforts.peakSpeed?.best)} /></dd></div>
            </dl>
            <div className="trend">
              <label className="inline-label">
                Trend
                <select value={metric.key} onChange={(e) => setMetricKey(e.target.value)}>
                  {TREND_METRICS.map((candidate) => <option key={candidate.key} value={candidate.key}>{candidate.label}</option>)}
                </select>
              </label>
              <EChart option={option} height={260} />
            </div>
            <table className="table">
              <thead>
                <tr>
                  <th>Session</th>
                  <th>Surface</th>
                  <th className="number">Segments</th>
                  <th className="number">Time</th>
                  <th className="number">Efforts</th>
                  <th className="number">Best peak speed</th>
                  <th className="number">Moving speed</th>
                </tr>
              </thead>
              <tbody>
                {sessions.map((row) => (
                  <tr key={row.sessionId}>
                    <td><Link to={`/sessions/${row.sessionId}`}>{row.startTime.slice(0, 10)}</Link></td>
                    <td><SurfaceChip surface={row.surface} /></td>
                    <td className="number">{row.segmentCount}</td>
                    <td className="number">{duration(row.metrics.time.elapsedSec)}</td>
                    <td className="number">{row.metrics.efforts.count}</td>
                    <td className="number">{speedKmh(row.metrics.efforts.peakSpeed?.best)}</td>
                    <td className="number">{speedKmh(row.metrics.distance.movingSpeed)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <aside>
            <MetricsPanel columns={[{ label: surface ? `All ${surface.toLowerCase()} segments` : 'All segments', metrics: totals }]} />
          </aside>
        </div>
      )}
    </section>
  )
}
