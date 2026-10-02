import { useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { type SessionFilter, useSessions } from '../../api/sessions'
import type { SessionSummary, Surface } from '../../api/types'
import { Badge, SurfaceChip } from '../../components/Chips'
import { endOfDay, startOfDay } from '../../dates'
import { duration, localDateTime, pace, speedKmh } from '../../format'
import { UploadDialog } from './UploadDialog'

const PAGE_SIZE = 20
const SURFACES: Surface[] = ['GRASS', 'SAND', 'UNKNOWN']

/** Screen 1 (spec 10.1): all sessions, filtered by surface and date range, newest first. */
export function SessionsPage() {
  const [params, setParams] = useSearchParams()
  const [uploading, setUploading] = useState(false)
  const surface = params.get('surface') as Surface | null
  const fromDay = params.get('from') ?? ''
  const toDay = params.get('to') ?? ''
  const page = Number(params.get('page') ?? 0)
  const filter: SessionFilter = {
    surface: surface ?? undefined,
    from: fromDay ? startOfDay(fromDay) : undefined,
    to: toDay ? endOfDay(toDay) : undefined,
    page,
    size: PAGE_SIZE,
  }
  const sessions = useSessions(filter)
  const filtered = Boolean(surface || fromDay || toDay)

  /** Changes URL parameters; a new filter starts again at the first page. */
  function update(changes: Record<string, string | null>) {
    const next = new URLSearchParams(params)
    for (const [key, value] of Object.entries(changes)) {
      if (value) next.set(key, value)
      else next.delete(key)
    }
    if (!('page' in changes)) next.delete('page')
    setParams(next)
  }

  return (
    <section>
      <div className="page-header">
        <h1>Sessions</h1>
        <button className="button primary" onClick={() => setUploading(true)}>Upload FIT files</button>
      </div>

      <div className="filters">
        <label>
          Surface
          <select value={surface ?? ''} onChange={(e) => update({ surface: e.target.value || null })}>
            <option value="">All</option>
            {SURFACES.map((s) => <option key={s} value={s}>{s.charAt(0) + s.slice(1).toLowerCase()}</option>)}
          </select>
        </label>
        <label>
          From
          <input type="date" value={fromDay} onChange={(e) => update({ from: e.target.value || null })} />
        </label>
        <label>
          To
          <input type="date" value={toDay} onChange={(e) => update({ to: e.target.value || null })} />
        </label>
        {filtered && (
          <button className="button link" onClick={() => update({ surface: null, from: null, to: null })}>
            Clear filters
          </button>
        )}
      </div>

      {sessions.isPending && <p>Loading sessions…</p>}
      {sessions.isError && <p role="alert">Could not load the sessions: {sessions.error.message}</p>}
      {sessions.data && sessions.data.totalItems === 0 && (
        <div className="empty">
          {filtered ? <p>No sessions match the filters.</p> : (
            <>
              <p>No sessions yet. Upload the FIT files of your trainings, or Garmin Connect exports (.zip).</p>
              <button className="button primary" onClick={() => setUploading(true)}>Upload FIT files</button>
            </>
          )}
        </div>
      )}
      {sessions.data && sessions.data.totalItems > 0 && (
        <>
          <SessionsTable sessions={sessions.data.items} />
          <div className="pagination">
            <button className="button" disabled={page === 0} onClick={() => update({ page: String(page - 1) })}>
              Previous
            </button>
            <span>
              Page {page + 1} of {sessions.data.totalPages} · {sessions.data.totalItems} sessions
            </span>
            <button
              className="button"
              disabled={page + 1 >= sessions.data.totalPages}
              onClick={() => update({ page: String(page + 1) })}
            >
              Next
            </button>
          </div>
        </>
      )}

      {uploading && <UploadDialog onClose={() => setUploading(false)} />}
    </section>
  )
}

function SessionsTable({ sessions }: { sessions: SessionSummary[] }) {
  const navigate = useNavigate()
  return (
    <table className="table sessions-table">
      <thead>
        <tr>
          <th>Date</th>
          <th>Location</th>
          <th>Surface</th>
          <th className="number">Duration</th>
          <th className="number">Active</th>
          <th className="number">Efforts</th>
          <th className="number">Max speed</th>
          <th className="number">Moving pace</th>
          <th />
        </tr>
      </thead>
      <tbody>
        {sessions.map((session) => (
          <tr key={session.id} className="clickable" onClick={() => navigate(`/sessions/${session.id}`)}>
            <td>
              <Link to={`/sessions/${session.id}`} onClick={(e) => e.stopPropagation()}>
                {localDateTime(session.startTime, session.localTzOffsetSec)}
              </Link>
            </td>
            <td>{session.geozoneName ?? <span className="muted">–</span>}</td>
            <td><SurfaceChip surface={session.surface} source={session.surfaceSource} /></td>
            <td className="number">{duration(session.elapsedSec)}</td>
            <td className="number">{duration(session.activeSec)}</td>
            <td className="number">{session.effortCount}</td>
            <td className="number">{speedKmh(session.maxSpeed)}</td>
            <td className="number">{pace(session.movingPaceSecPerKm)}</td>
            <td>
              <div className="badges">
                  {session.recordingMode === 'SMART' && (
                  <Badge title="Recorded with Smart recording: sprint and acceleration numbers are low-confidence. Record with Every Second.">
                    Smart recording
                  </Badge>
                )}
                {session.outdated && (
                  <Badge tone="warning" title="Analysed with an older analysis version; recompute it on the session screen.">
                    outdated
                  </Badge>
                )}
              </div>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}
