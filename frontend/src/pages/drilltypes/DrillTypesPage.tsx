import { type FormEvent, useState } from 'react'
import { Link } from 'react-router'
import { useCreateDrillType, useDeleteDrillType, useDrillTypes, useUpdateDrillType } from '../../api/drillTypes'
import type { DrillType } from '../../api/types'
import { codeFromName, type DrillKind, KIND_LABELS, nextColor, PALETTE } from './drillTypeFormat'

/** A drill type being created (no id) or edited. */
interface Editing {
  id: string | null
  name: string
  code: string
  /** Whether the code was typed; until then it follows the name. */
  codeTyped: boolean
  kind: DrillKind
  color: string
}

/**
 * Screen 5, list (spec 10.1): the drill types with colour and kind; create, edit and delete them. Their statistics
 * are on each type's page.
 */
export function DrillTypesPage() {
  const drillTypes = useDrillTypes()
  const remove = useDeleteDrillType()
  const [editing, setEditing] = useState<Editing | null>(null)

  function startNew() {
    setEditing({ id: null, name: '', code: '', codeTyped: false, kind: 'DRILL', color: nextColor(drillTypes.data ?? []) })
  }

  function startEdit(type: DrillType) {
    setEditing({ id: type.id, name: type.name, code: type.code, codeTyped: true, kind: type.kind, color: type.color })
  }

  function confirmDelete(type: DrillType) {
    if (window.confirm(`Delete the drill type ${type.name}? Its segments stay, without a type.`)) remove.mutate(type.id)
  }

  return (
    <section className="drill-types-page">
      <div className="page-header">
        <h1>Drill types</h1>
        <button className="button primary" disabled={editing !== null} onClick={startNew}>New drill type</button>
      </div>
      <p className="muted">
        Drill types name and colour the segments of your sessions; each type's page compares its segments across
        sessions.
      </p>
      {remove.isError && <p role="alert" className="error">{remove.error.message}</p>}
      {editing && <DrillTypeForm editing={editing} onChange={setEditing} onDone={() => setEditing(null)} />}

      {drillTypes.isPending && <p>Loading drill types…</p>}
      {drillTypes.isError && <p role="alert">Could not load the drill types: {drillTypes.error.message}</p>}
      {drillTypes.data?.length === 0 && !editing && (
        <div className="empty">No drill types yet. Create the kinds of work you do in training, e.g. Warm-up, Cutting, Game.</div>
      )}
      {drillTypes.data && drillTypes.data.length > 0 && (
        <table className="table drill-types-table">
          <thead>
            <tr>
              <th>Name</th>
              <th>Kind</th>
              <th>Code</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {drillTypes.data.map((type) => (
              <tr key={type.id}>
                <td>
                  <span className="swatch large" style={{ background: type.color }} />
                  <Link to={`/drill-types/${type.id}`}>{type.name}</Link>
                </td>
                <td>{KIND_LABELS[type.kind]}</td>
                <td className="code">{type.code}</td>
                <td className="row-actions">
                  <button className="button small" disabled={editing !== null} onClick={() => startEdit(type)}>Edit</button>
                  <button className="button small" disabled={remove.isPending} onClick={() => confirmDelete(type)}>Delete</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  )
}

interface FormProps {
  editing: Editing
  onChange: (editing: Editing) => void
  onDone: () => void
}

/** Name, code, kind and colour; a taken code (409) is explained inline. */
function DrillTypeForm({ editing, onChange, onDone }: FormProps) {
  const create = useCreateDrillType()
  const update = useUpdateDrillType()
  const mutation = editing.id ? update : create
  const set = (change: Partial<Editing>) => onChange({ ...editing, ...change })
  const title = editing.id ? `Edit ${editing.name}` : 'New drill type'

  function submit(event: FormEvent) {
    event.preventDefault()
    const body = { name: editing.name.trim(), code: editing.code, kind: editing.kind, color: editing.color }
    if (editing.id) update.mutate({ id: editing.id, patch: body }, { onSuccess: onDone })
    else create.mutate(body, { onSuccess: onDone })
  }

  return (
    <form
      className="drill-type-form"
      aria-label={title}
      onSubmit={submit}
      onKeyDown={(event) => {
        if (event.key === 'Escape') onDone()
      }}
    >
      <label>
        Name
        <input
          value={editing.name}
          required
          autoFocus
          placeholder="e.g. Cutting 1v1"
          onChange={(e) => set({ name: e.target.value, code: editing.codeTyped ? editing.code : codeFromName(e.target.value) })}
        />
      </label>
      <label>
        Code
        <input
          value={editing.code}
          required
          pattern="[A-Z0-9_]+"
          title="Upper case letters, digits and _"
          onChange={(e) => set({ code: e.target.value.toUpperCase(), codeTyped: true })}
        />
      </label>
      <label>
        Kind
        <select value={editing.kind} onChange={(e) => set({ kind: e.target.value as DrillKind })}>
          {Object.entries(KIND_LABELS).map(([kind, label]) => <option key={kind} value={kind}>{label}</option>)}
        </select>
      </label>
      <fieldset className="color-field">
        <legend>Colour</legend>
        {PALETTE.map((color) => (
          <button
            key={color}
            type="button"
            className={`swatch-button ${editing.color.toLowerCase() === color ? 'chosen' : ''}`}
            style={{ background: color }}
            aria-label={`Colour ${color}`}
            aria-pressed={editing.color.toLowerCase() === color}
            onClick={() => set({ color })}
          />
        ))}
        <input type="color" aria-label="Other colour" value={editing.color} onChange={(e) => set({ color: e.target.value })} />
      </fieldset>
      <div className="form-buttons">
        <button className="button small primary" type="submit" disabled={mutation.isPending}>Save</button>
        <button className="button small link" type="button" onClick={onDone}>Cancel</button>
      </div>
      {mutation.isError && <p role="alert" className="error">{mutation.error.message}</p>}
    </form>
  )
}
