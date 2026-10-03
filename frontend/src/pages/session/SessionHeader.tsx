import { useNavigate } from 'react-router'
import { confirmDeleteSessions, useDeleteSessions, useRecomputeSession, useUpdateSession } from '../../api/sessions'
import type { SessionDetail, Surface, SurfaceSource } from '../../api/types'
import { Badge } from '../../components/Chips'
import { MetricValue } from '../../components/MetricValue'
import { distance, duration, localDateTime, pace, speedKmh } from '../../format'

/** Where the surface comes from, next to it. */
const SURFACE_SOURCES: Record<SurfaceSource, string> = {
  GEOZONE: 'from the geozone',
  MANUAL: 'set by hand',
  NONE: 'no geozone here',
}

/** Date, place, surface and key metrics of a session, with its actions (spec 10.2). */
export function SessionHeader({ session }: { session: SessionDetail }) {
  const updateSession = useUpdateSession()
  const recompute = useRecomputeSession()
  const deleteSessions = useDeleteSessions()
  const navigate = useNavigate()
  const failed = [updateSession, recompute, deleteSessions].find((mutation) => mutation.isError)
  const metrics = session.metrics
  const keyMetrics: [string, string][] = [
    ['Duration', duration(session.elapsedSec)],
    ['Active', duration(metrics.time.activeSec)],
    ['Distance', distance(metrics.distance.distanceM)],
    ['Efforts', String(metrics.efforts.count)],
    ['Max speed', speedKmh(metrics.distance.maxSpeed)],
    ['Moving pace', pace(metrics.distance.movingPaceSecPerKm)],
  ]

  function deleteSession() {
    if (!confirmDeleteSessions(1)) return
    deleteSessions.mutate([session.id], { onSuccess: () => navigate('/', { replace: true }) })
  }

  return (
    <header className="session-header">
      <div className="session-title">
        <h1>{localDateTime(session.startTime, session.localTzOffsetSec)}</h1>
        <span className="session-place">{session.geozoneName ?? 'Unknown place'}</span>
        <label className="inline-label">
          Surface
          <select
            value={session.surface}
            disabled={updateSession.isPending}
            onChange={(e) => updateSession.mutate({ id: session.id, patch: { surface: e.target.value as Surface } })}
          >
            <option value="GRASS">Grass</option>
            <option value="SAND">Sand</option>
            <option value="UNKNOWN">Unknown</option>
          </select>
        </label>
        <span className="muted">{SURFACE_SOURCES[session.surfaceSource]}</span>
        <span className="session-actions">
          {session.outdated && (
            <Badge tone="warning" title="Analysed with an older analysis version.">outdated</Badge>
          )}
          <button
            className={`button small ${session.outdated ? 'primary' : ''}`}
            disabled={recompute.isPending}
            onClick={() => recompute.mutate(session.id)}
            title="Analyse the session again from its FIT file; segments, notes and a manual surface stay."
          >
            {recompute.isPending ? 'Recomputing…' : 'Recompute'}
          </button>
          <button className="button small danger" disabled={deleteSessions.isPending} onClick={deleteSession}>
            Delete
          </button>
        </span>
      </div>
      {session.recordingMode === 'SMART' && (
        <p className="notice">
          Recorded with Smart recording (samples up to 6 s apart): sprint and acceleration numbers are low-confidence.
          Set the watch to record every second.
        </p>
      )}
      <dl className="key-metrics">
        {keyMetrics.map(([label, value]) => (
          <div key={label}>
            <dt>{label}</dt>
            <dd><MetricValue value={value} /></dd>
          </div>
        ))}
      </dl>
      {failed && <p role="alert" className="error">{failed.error?.message}</p>}
    </header>
  )
}
