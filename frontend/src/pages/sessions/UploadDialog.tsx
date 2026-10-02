import { type DragEvent, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router'
import { useCreateGeozone } from '../../api/geozones'
import { useSession, useUpdateSession, useUploadSessions } from '../../api/sessions'
import type { Surface, UploadResult } from '../../api/types'
import { SurfaceChip } from '../../components/Chips'

const STATUS_LABELS: Record<UploadResult['status'], string> = {
  CREATED: 'created',
  DUPLICATE: 'already uploaded',
  FAILED: 'failed',
}

/**
 * Screen 2 (spec 10.1): upload dialog over the sessions list. Files are dropped or chosen; every FIT file, also inside
 * a .zip, gets a result row. Sessions at an unknown place get a surface here, by hand or with a new geozone.
 */
export function UploadDialog({ onClose }: { onClose: () => void }) {
  const dialog = useRef<HTMLDialogElement>(null)
  const upload = useUploadSessions()
  const [results, setResults] = useState<UploadResult[]>([])
  const [dragging, setDragging] = useState(false)

  useEffect(() => {
    const element = dialog.current
    if (element && !element.open) {
      if (typeof element.showModal === 'function') element.showModal()
      else element.setAttribute('open', '')
    }
  }, [])

  function send(files: File[]) {
    if (files.length === 0) return
    upload.mutate(files, { onSuccess: (batch) => setResults((previous) => [...batch, ...previous]) })
  }

  function drop(event: DragEvent) {
    event.preventDefault()
    setDragging(false)
    send([...event.dataTransfer.files])
  }

  return (
    <dialog ref={dialog} className="dialog upload-dialog" aria-labelledby="upload-title" onClose={onClose}>
      <div className="dialog-header">
        <h2 id="upload-title">Upload trainings</h2>
        <button className="button link" onClick={onClose} aria-label="Close">✕</button>
      </div>

      <div
        className={`dropzone ${dragging ? 'dragging' : ''}`}
        onDragOver={(event) => {
          event.preventDefault()
          setDragging(true)
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={drop}
      >
        <p>Drop .fit files or Garmin Connect exports (.zip) here</p>
        <label className="button">
          Choose files
          <input
            type="file"
            accept=".fit,.zip"
            multiple
            hidden
            onChange={(event) => {
              send([...(event.target.files ?? [])])
              event.target.value = ''
            }}
          />
        </label>
      </div>

      {upload.isPending && <p className="muted">Uploading and analysing…</p>}
      {upload.isError && <p role="alert">Upload failed: {upload.error.message}</p>}

      {results.length > 0 && (
        <ul className="upload-results">
          {results.map((result, index) => (
            <li key={`${result.fileName}-${index}`} className="upload-row">
              <span className="file-name">{result.fileName}</span>
              <span className={`status status-${result.status.toLowerCase()}`}>{STATUS_LABELS[result.status]}</span>
              {result.error && <span className="error">{result.error}</span>}
              {result.sessionId && <Link to={`/sessions/${result.sessionId}`} onClick={onClose}>Open</Link>}
              {result.status === 'CREATED' && result.needsSurface && result.sessionId && (
                <SurfacePicker sessionId={result.sessionId} />
              )}
            </li>
          ))}
        </ul>
      )}
    </dialog>
  )
}

/**
 * For a session no geozone matched: pick the surface by hand, or create a geozone around its start, which also
 * classifies other sessions at that place (spec 6.6). Shows the surface once it is known.
 */
function SurfacePicker({ sessionId }: { sessionId: string }) {
  const session = useSession(sessionId)
  const updateSession = useUpdateSession()
  const createGeozone = useCreateGeozone()
  const [creating, setCreating] = useState(false)
  const [name, setName] = useState('Training field')
  const [surface, setSurface] = useState<Surface>('GRASS')
  const [radiusM, setRadiusM] = useState(150)

  if (!session.data) return null
  if (session.data.surface !== 'UNKNOWN') {
    return (
      <span className="surface-picked">
        <SurfaceChip surface={session.data.surface} source={session.data.surfaceSource} />
        {session.data.geozoneName && <span className="muted">{session.data.geozoneName}</span>}
      </span>
    )
  }
  const start = session.data.startPosition
  const busy = updateSession.isPending || createGeozone.isPending

  return (
    <div className="surface-picker">
      <span className="muted">Unknown place:</span>
      {(['GRASS', 'SAND'] as const).map((value) => (
        <button
          key={value}
          className="button small"
          disabled={busy}
          onClick={() => updateSession.mutate({ id: sessionId, patch: { surface: value } })}
        >
          {value === 'GRASS' ? 'Grass' : 'Sand'}
        </button>
      ))}
      {start && !creating && (
        <button className="button small" disabled={busy} onClick={() => setCreating(true)}>
          Create geozone from this session
        </button>
      )}
      {start && creating && (
        <form
          className="geozone-form"
          onSubmit={(event) => {
            event.preventDefault()
            createGeozone.mutate({ name, surface, shape: { type: 'circle', lat: start.lat, lon: start.lon, radiusM } })
          }}
        >
          <label>
            Name <input value={name} onChange={(e) => setName(e.target.value)} required />
          </label>
          <label>
            Surface
            <select value={surface} onChange={(e) => setSurface(e.target.value as Surface)}>
              <option value="GRASS">Grass</option>
              <option value="SAND">Sand</option>
            </select>
          </label>
          <label>
            Radius (m)
            <input type="number" min={20} max={5000} value={radiusM} onChange={(e) => setRadiusM(Number(e.target.value))} />
          </label>
          <button className="button small primary" type="submit" disabled={busy}>Create</button>
        </form>
      )}
      {(updateSession.isError || createGeozone.isError) && (
        <span role="alert" className="error">{(updateSession.error ?? createGeozone.error)?.message}</span>
      )}
    </div>
  )
}
