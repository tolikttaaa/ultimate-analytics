import { lazy, Suspense, useCallback, useEffect, useState } from 'react'
import { useParams, useSearchParams } from 'react-router'
import { ApiError } from '../../api/client'
import { useUiConfig } from '../../api/config'
import { useDrillTypes } from '../../api/drillTypes'
import { useGeozones } from '../../api/geozones'
import { useEfforts, useSession, useSessionSeries, useWindowMetrics } from '../../api/sessions'
import { SessionCharts } from '../../components/charts/SessionCharts'
import { useDebouncedValue } from '../../useDebouncedValue'
import { ActivityStrip } from './ActivityStrip'
import { createCursor } from './cursor'
import { EffortDrawer } from './EffortDrawer'
import { type MetricsColumn, MetricsPanel } from './MetricsPanel'
import { SegmentStrip } from './SegmentStrip'
import { SessionHeader } from './SessionHeader'
import { parseWindow, type TimeWindow } from './timeWindow'
import { WindowBar } from './WindowBar'

// MapLibre is a chunk of its own, so the charts do not wait for it.
const SessionMap = lazy(() => import('../../components/map/SessionMap').then((module) => ({ default: module.SessionMap })))

const METRICS_DEBOUNCE_MS = 200

/** Screen 3 (spec 10.1, 10.2): header, segment strip, synchronised charts, and map and metrics on the right. */
export function SessionPage() {
  const { id = '' } = useParams()
  const session = useSession(id)
  const series = useSessionSeries(id)
  const efforts = useEfforts(id)
  const drillTypes = useDrillTypes()
  const config = useUiConfig()
  const geozones = useGeozones()
  const [params, setParams] = useSearchParams()
  const [view, setView] = useState<TimeWindow | null>(null)
  const [cursor] = useState(createCursor)
  const [effortId, setEffortId] = useState<string | null>(null)

  // The selected window lives in the URL (spec 10.2): ?from=600&to=1200.
  const lastT = session.data?.elapsedSec ?? 0
  const selection = parseWindow(params, lastT)
  const selectionKey = selection ? selection.join('-') : ''
  const setSelection = useCallback((window: TimeWindow | null) => {
    setParams((current) => {
      const next = new URLSearchParams(current)
      if (window) {
        next.set('from', String(window[0]))
        next.set('to', String(window[1]))
      } else {
        next.delete('from')
        next.delete('to')
      }
      return next
    }, { replace: true })
  }, [setParams])
  const settledKey = useDebouncedValue(selectionKey, METRICS_DEBOUNCE_MS)
  const settled = settledKey && settledKey === selectionKey ? selection : null
  const windowMetrics = useWindowMetrics(id, settled)

  // Escape closes the effort drawer, else clears the selection; menus and forms handle it first.
  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if (event.key !== 'Escape' || event.defaultPrevented) return
      if (effortId) setEffortId(null)
      else setSelection(null)
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [effortId, setSelection])

  if (session.isError) {
    const missing = session.error instanceof ApiError && session.error.status === 404
    return <p role="alert">{missing ? 'This session does not exist.' : `Could not load the session: ${session.error.message}`}</p>
  }
  if (!session.data) return <p>Loading session…</p>
  const geozone = geozones.data?.find((zone) => zone.id === session.data.geozoneId) ?? null
  const columns: MetricsColumn[] = [{ label: 'Session', metrics: session.data.metrics }]
  if (selection) columns.push({ label: 'Window', metrics: settled ? windowMetrics.data : undefined })

  return (
    <div className="session-page">
      <SessionHeader session={session.data} />
      <div className="session-body">
        <div className="session-main">
          <SegmentStrip
            sessionId={id}
            segments={session.data.segments}
            drillTypes={drillTypes.data ?? []}
            view={view ?? [0, lastT]}
            lastT={lastT}
            selection={selection}
            onSelect={setSelection}
          />
          {series.data && <ActivityStrip series={series.data} view={view ?? [0, lastT]} onSelect={setSelection} />}
          <WindowBar
            sessionId={id}
            selection={selection}
            segments={session.data.segments}
            drillTypes={drillTypes.data ?? []}
            onClear={() => setSelection(null)}
          />
          {series.data && efforts.data ? (
            <SessionCharts
              series={series.data}
              efforts={efforts.data}
              segments={session.data.segments}
              drillTypes={drillTypes.data ?? []}
              lastT={lastT}
              selection={selection}
              onSelect={setSelection}
              onEffortClick={setEffortId}
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
                  segments={session.data.segments}
                  drillTypes={drillTypes.data ?? []}
                  timeWindow={selection}
                  geozone={geozone}
                  cursor={cursor}
                />
              </Suspense>
            ) : (
              <div className="map-placeholder">Loading map…</div>
            )}
          </div>
          {windowMetrics.isError && <p role="alert" className="error">Window metrics: {windowMetrics.error.message}</p>}
          <MetricsPanel columns={columns} />
        </aside>
      </div>
      {effortId && series.data && efforts.data && (
        <EffortDrawer
          effortId={effortId}
          efforts={efforts.data}
          series={series.data}
          mapConfig={config.data?.map}
          onSelect={setEffortId}
          onClose={() => setEffortId(null)}
        />
      )}
    </div>
  )
}
