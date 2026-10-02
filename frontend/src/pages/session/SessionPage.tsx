import { lazy, Suspense, useState } from 'react'
import { useParams, useSearchParams } from 'react-router'
import { ApiError } from '../../api/client'
import { useUiConfig } from '../../api/config'
import { useDrillTypes } from '../../api/drillTypes'
import { useGeozones } from '../../api/geozones'
import { useEfforts, useSession, useSessionSeries } from '../../api/sessions'
import { SessionCharts } from '../../components/charts/SessionCharts'
import { createCursor } from './cursor'
import { MetricsPanel } from './MetricsPanel'
import { SegmentStrip } from './SegmentStrip'
import { SessionHeader } from './SessionHeader'
import { parseWindow } from './timeWindow'

// MapLibre is a chunk of its own, so the charts do not wait for it.
const SessionMap = lazy(() => import('../../components/map/SessionMap').then((module) => ({ default: module.SessionMap })))

/** Screen 3 (spec 10.1, 10.2): header, segment strip, synchronised charts, and map and metrics on the right. */
export function SessionPage() {
  const { id = '' } = useParams()
  const session = useSession(id)
  const series = useSessionSeries(id)
  const efforts = useEfforts(id)
  const drillTypes = useDrillTypes()
  const config = useUiConfig()
  const geozones = useGeozones()
  const [params] = useSearchParams()
  const [view, setView] = useState<[number, number] | null>(null)
  const [cursor] = useState(createCursor)

  if (session.isError) {
    const missing = session.error instanceof ApiError && session.error.status === 404
    return <p role="alert">{missing ? 'This session does not exist.' : `Could not load the session: ${session.error.message}`}</p>
  }
  if (!session.data) return <p>Loading session…</p>
  const lastT = session.data.elapsedSec
  const timeWindow = parseWindow(params, lastT)
  const geozone = geozones.data?.find((zone) => zone.id === session.data.geozoneId) ?? null

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
              onCursor={cursor.set}
            />
          ) : (
            <p className="muted">Loading charts…</p>
          )}
        </div>
        <aside className="session-side">
          <div className="session-map">
            {config.data && series.data && efforts.data ? (
              <Suspense fallback={<div className="map-placeholder">Loading map…</div>}>
                <SessionMap
                  config={config.data.map}
                  series={series.data}
                  efforts={efforts.data}
                  timeWindow={timeWindow}
                  geozone={geozone}
                  cursor={cursor}
                />
              </Suspense>
            ) : (
              <div className="map-placeholder">Loading map…</div>
            )}
          </div>
          <MetricsPanel columns={[{ label: 'Session', metrics: session.data.metrics }]} />
        </aside>
      </div>
    </div>
  )
}
