import { useState } from 'react'
import { useParams } from 'react-router'
import { ApiError } from '../../api/client'
import { useDrillTypes } from '../../api/drillTypes'
import { useEfforts, useSession, useSessionSeries } from '../../api/sessions'
import { SessionCharts } from '../../components/charts/SessionCharts'
import { MetricsPanel } from './MetricsPanel'
import { SegmentStrip } from './SegmentStrip'
import { SessionHeader } from './SessionHeader'

/** Screen 3 (spec 10.1, 10.2): header, segment strip, synchronised charts, and map and metrics on the right. */
export function SessionPage() {
  const { id = '' } = useParams()
  const session = useSession(id)
  const series = useSessionSeries(id)
  const efforts = useEfforts(id)
  const drillTypes = useDrillTypes()
  const [view, setView] = useState<[number, number] | null>(null)

  if (session.isError) {
    const missing = session.error instanceof ApiError && session.error.status === 404
    return <p role="alert">{missing ? 'This session does not exist.' : `Could not load the session: ${session.error.message}`}</p>
  }
  if (!session.data) return <p>Loading session…</p>
  const lastT = session.data.elapsedSec

  return (
    <div className="session-page">
      <SessionHeader session={session.data} />
      <div className="session-body">
        <div className="session-main">
          <SegmentStrip segments={session.data.segments} drillTypes={drillTypes.data ?? []} view={view ?? [0, lastT]} />
          {series.data && efforts.data ? (
            <SessionCharts
              series={series.data}
              efforts={efforts.data}
              segments={session.data.segments}
              drillTypes={drillTypes.data ?? []}
              lastT={lastT}
              onZoom={setView}
            />
          ) : (
            <p className="muted">Loading charts…</p>
          )}
        </div>
        <aside className="session-side">
          <div className="map-placeholder">Map</div>
          <MetricsPanel columns={[{ label: 'Session', metrics: session.data.metrics }]} />
        </aside>
      </div>
    </div>
  )
}
