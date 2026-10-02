import type { Position } from 'geojson'
import { type FormEvent, lazy, Suspense, useEffect, useState } from 'react'
import { useUiConfig } from '../../api/config'
import { useCreateGeozone, useDeleteGeozone, useGeozones, useUpdateGeozone } from '../../api/geozones'
import { useSession, useSessions } from '../../api/sessions'
import type { Geozone } from '../../api/types'
import { SurfaceChip } from '../../components/Chips'
import {
  addPoint,
  DEFAULT_RADIUS_M,
  type Draft,
  describeShape,
  draftShape,
  finishPolygon,
  MIN_POLYGON_CORNERS,
  shapeDraft,
  undoPoint,
} from './draft'

// MapLibre is a chunk of its own.
const GeozoneMap = lazy(() => import('../../components/map/GeozoneMap').then((module) => ({ default: module.GeozoneMap })))

type GeozoneSurface = 'GRASS' | 'SAND'

/** A geozone being created (no id) or edited. */
interface Editing {
  id: string | null
  name: string
  surface: GeozoneSurface
  draft: Draft
}

/** What a change did to the sessions (spec 6.6). */
function sessionsChanged(count: number): string {
  if (count === 0) return 'No session changed its surface.'
  return count === 1 ? '1 session was classified again.' : `${count} sessions were classified again.`
}

/**
 * Screen 4 (spec 10.1): all geozones on a map and in a list; create circles (click + radius) and polygons (click the
 * corners), edit and delete them. Every change matches the sessions again.
 */
export function GeozonesPage() {
  const config = useUiConfig()
  const geozones = useGeozones()
  const create = useCreateGeozone()
  const update = useUpdateGeozone()
  const remove = useDeleteGeozone()
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [focusId, setFocusId] = useState<string | null>(null)
  const [editing, setEditing] = useState<Editing | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  // Without geozones the map starts at the latest session, where the next field probably is.
  const noGeozones = geozones.data?.length === 0
  const latest = useSessions({ page: 0, size: 1 })
  const latestSession = useSession(noGeozones ? latest.data?.items[0]?.id ?? '' : '')
  const start = latestSession.data?.startPosition
  const fallbackCenter: Position | null = start ? [start.lon, start.lat] : null
  const mapReady = config.data && geozones.data && !(noGeozones && (latest.isLoading || latestSession.isLoading))

  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape' && !event.defaultPrevented) setEditing(null)
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [])

  function startNew(kind: Draft['kind']) {
    setNotice(null)
    setSelectedId(null)
    setEditing({
      id: null,
      name: '',
      surface: 'GRASS',
      draft: kind === 'circle' ? { kind, center: null, radiusM: DEFAULT_RADIUS_M } : { kind, points: [], closed: false },
    })
  }

  function startEdit(geozone: Geozone) {
    setNotice(null)
    setSelectedId(geozone.id)
    setFocusId(geozone.id)
    setEditing({ id: geozone.id, name: geozone.name, surface: geozone.surface === 'SAND' ? 'SAND' : 'GRASS', draft: shapeDraft(geozone.shape) })
  }

  function changeDraft(change: (draft: Draft) => Draft) {
    setEditing((current) => current && { ...current, draft: change(current.draft) })
  }

  function save(event: FormEvent) {
    event.preventDefault()
    const shape = editing && draftShape(editing.draft)
    if (!editing || !shape) return
    const body = { name: editing.name.trim(), surface: editing.surface, shape }
    const done = (verb: string) => (result: { geozone?: Geozone | null; affectedSessionCount: number }) => {
      setNotice(`${verb} ${body.name}. ${sessionsChanged(result.affectedSessionCount)}`)
      setSelectedId(result.geozone?.id ?? null)
      setEditing(null)
    }
    if (editing.id) update.mutate({ id: editing.id, patch: body }, { onSuccess: done('Saved') })
    else create.mutate(body, { onSuccess: done('Created') })
  }

  function confirmDelete(geozone: Geozone) {
    if (!window.confirm(`Delete the geozone ${geozone.name}? Its sessions are matched again and may lose their surface.`)) return
    remove.mutate(geozone.id, {
      onSuccess: (result) => {
        setNotice(`Deleted ${geozone.name}. ${sessionsChanged(result.affectedSessionCount)}`)
        setSelectedId(null)
        if (editing?.id === geozone.id) setEditing(null)
      },
    })
  }

  const failed = [create, update, remove].find((mutation) => mutation.isError)

  return (
    <section className="geozones-page">
      <div className="geozones-side">
        <div className="page-header">
          <h1>Geozones</h1>
          <span className="geozone-new">
            <button className="button small" disabled={editing !== null} onClick={() => startNew('circle')}>New circle</button>
            <button className="button small" disabled={editing !== null} onClick={() => startNew('polygon')}>New polygon</button>
          </span>
        </div>
        <p className="muted geozones-intro">
          A geozone is an area with a surface. Sessions starting inside it get its surface, unless you set one by hand.
        </p>
        {notice && <p role="status" className="notice">{notice}</p>}
        {failed && <p role="alert" className="error">{failed.error?.message}</p>}

        {editing && (
          <GeozoneEditor
            editing={editing}
            pending={create.isPending || update.isPending}
            onChange={(change) => setEditing({ ...editing, ...change })}
            onDraft={changeDraft}
            onSave={save}
            onCancel={() => setEditing(null)}
          />
        )}

        {geozones.isPending && <p>Loading geozones…</p>}
        {geozones.isError && <p role="alert">Could not load the geozones: {geozones.error.message}</p>}
        {noGeozones && !editing && (
          <div className="empty">No geozones yet. Draw one around a field with <b>New circle</b> or <b>New polygon</b>.</div>
        )}
        {geozones.data && geozones.data.length > 0 && (
          <ul className="geozone-list" aria-label="Geozones">
            {geozones.data.map((geozone) => (
              <li
                key={geozone.id}
                className={`geozone-row ${geozone.id === selectedId ? 'selected' : ''}`}
                onClick={() => {
                  setSelectedId(geozone.id)
                  setFocusId(geozone.id)
                }}
              >
                <span className="geozone-name">{geozone.name}</span>
                <SurfaceChip surface={geozone.surface} source="GEOZONE" />
                <span className="muted">{describeShape(geozone.shape)}</span>
                <span className="geozone-actions">
                  <button className="button small" disabled={editing !== null} onClick={(e) => { e.stopPropagation(); startEdit(geozone) }}>
                    Edit
                  </button>
                  <button className="button small" disabled={remove.isPending} onClick={(e) => { e.stopPropagation(); confirmDelete(geozone) }}>
                    Delete
                  </button>
                </span>
              </li>
            ))}
          </ul>
        )}
      </div>

      <div className="geozones-map">
        {mapReady ? (
          <Suspense fallback={<div className="map-placeholder">Loading map…</div>}>
            <GeozoneMap
              config={config.data!.map}
              geozones={geozones.data!}
              selectedId={selectedId}
              focusId={focusId}
              draft={editing?.draft ?? null}
              fallbackCenter={fallbackCenter}
              onMapClick={(position) => changeDraft((draft) => addPoint(draft, position))}
              onFinish={() => changeDraft(finishPolygon)}
              onSelect={setSelectedId}
            />
          </Suspense>
        ) : (
          <div className="map-placeholder">Loading map…</div>
        )}
      </div>
    </section>
  )
}

interface EditorProps {
  editing: Editing
  pending: boolean
  onChange: (change: Partial<Pick<Editing, 'name' | 'surface'>>) => void
  onDraft: (change: (draft: Draft) => Draft) => void
  onSave: (event: FormEvent) => void
  onCancel: () => void
}

/** Name, surface and shape of the geozone being created or edited, with what to do next on the map. */
function GeozoneEditor({ editing, pending, onChange, onDraft, onSave, onCancel }: EditorProps) {
  const { draft } = editing
  const shapeReady = draftShape(draft) !== null
  const title = `${editing.id ? 'Edit' : 'New'} ${draft.kind}`
  let hint: string
  if (draft.kind === 'circle') hint = draft.center ? 'Click the map to move the centre.' : 'Click the centre of the field on the map.'
  else if (draft.closed) hint = `${draft.points.length} corners. Redraw to change them.`
  else hint = `Click the corners on the map (${draft.points.length} so far); double-click the last one or press Finish.`

  return (
    <form className="geozone-editor" aria-label={title} onSubmit={onSave}>
      <h2>{title}</h2>
      <p className="muted">{hint}</p>
      <label>
        Name
        <input value={editing.name} onChange={(e) => onChange({ name: e.target.value })} required placeholder="e.g. Beach courts" />
      </label>
      <label>
        Surface
        <select value={editing.surface} onChange={(e) => onChange({ surface: e.target.value as GeozoneSurface })}>
          <option value="GRASS">Grass</option>
          <option value="SAND">Sand</option>
        </select>
      </label>
      {draft.kind === 'circle' && (
        <label>
          Radius (m)
          <input
            type="number"
            min={10}
            max={5000}
            value={draft.radiusM}
            onChange={(e) => onDraft(() => ({ ...draft, radiusM: Number(e.target.value) }))}
          />
        </label>
      )}
      {draft.kind === 'polygon' && (
        <div className="geozone-draw">
          {!draft.closed && (
            <>
              <button type="button" className="button small" disabled={draft.points.length === 0} onClick={() => onDraft(undoPoint)}>
                Undo corner
              </button>
              <button
                type="button"
                className="button small"
                disabled={draft.points.length < MIN_POLYGON_CORNERS}
                onClick={() => onDraft(finishPolygon)}
              >
                Finish
              </button>
            </>
          )}
          {draft.closed && (
            <button type="button" className="button small" onClick={() => onDraft(() => ({ kind: 'polygon', points: [], closed: false }))}>
              Redraw
            </button>
          )}
        </div>
      )}
      <div className="geozone-buttons">
        <button className="button small primary" type="submit" disabled={pending || !shapeReady || editing.name.trim() === ''}>
          Save
        </button>
        <button className="button small link" type="button" onClick={onCancel}>Cancel</button>
      </div>
    </form>
  )
}
