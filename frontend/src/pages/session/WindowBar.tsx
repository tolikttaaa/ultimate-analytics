import { type FormEvent, useState } from 'react'
import { useCreateSegment } from '../../api/segments'
import type { DrillType, Segment } from '../../api/types'
import { segmentLabel } from '../../components/charts/sessionChartOptions'
import { duration } from '../../format'
import { type Cut, cutsBy } from './strip'
import type { TimeWindow } from './timeWindow'

interface Props {
  sessionId: string
  selection: TimeWindow | null
  segments: Segment[]
  drillTypes: DrillType[]
  onClear: () => void
}

/** The selected window (spec 10.2): its range, "Save as segment" and clearing it. */
export function WindowBar({ sessionId, selection, segments, drillTypes, onClear }: Props) {
  const [saving, setSaving] = useState(false)

  if (!selection) {
    return <div className="window-bar muted">Drag across a chart to select a window, or click a segment.</div>
  }
  const [from, to] = selection
  return (
    <div className="window-bar">
      <span>
        Window <b>{duration(from)}–{duration(to)}</b> <span className="muted">({duration(to - from)})</span>
      </span>
      {!saving && <button className="button small" onClick={() => setSaving(true)}>Save as segment</button>}
      <button className="button small link" onClick={onClear} title="Escape">Clear</button>
      {saving && (
        <SegmentForm
          key={`${from}-${to}`}
          sessionId={sessionId}
          selection={selection}
          segments={segments}
          drillTypes={drillTypes}
          onDone={() => setSaving(false)}
        />
      )}
    </div>
  )
}

interface FormProps {
  sessionId: string
  selection: TimeWindow
  segments: Segment[]
  drillTypes: DrillType[]
  onDone: () => void
}

/** How saving changes a segment the window overlaps, e.g. "Lap 1 is split around it". */
function describeCut(cut: Cut, drillTypes: DrillType[]): string {
  const name = segmentLabel(cut.segment, drillTypes)
  if (cut.kind === 'split') return `${name} is split around it`
  if (cut.kind === 'replaced') return `${name} is replaced`
  return `${name} is shortened to ${duration(cut.to[0])}–${duration(cut.to[1])}`
}

/** Drill type and label of the new segment; segments in the way are cut, and the form says how. */
function SegmentForm({ sessionId, selection: [from, to], segments, drillTypes, onDone }: FormProps) {
  const create = useCreateSegment(sessionId)
  const [drillTypeId, setDrillTypeId] = useState('')
  const [label, setLabel] = useState('')

  function submit(event: FormEvent) {
    event.preventDefault()
    create.mutate(
      { startT: from, endT: to, drillTypeId: drillTypeId || null, label: label.trim() || null },
      { onSuccess: onDone },
    )
  }

  const cuts = cutsBy([from, to], segments)

  return (
    <form
      className="segment-form"
      onSubmit={submit}
      onKeyDown={(event) => {
        if (event.key === 'Escape') {
          event.preventDefault()
          onDone()
        }
      }}
    >
      <label>
        Drill type
        <select value={drillTypeId} onChange={(e) => setDrillTypeId(e.target.value)} autoFocus>
          <option value="">No type</option>
          {drillTypes.map((type) => <option key={type.id} value={type.id}>{type.name}</option>)}
        </select>
      </label>
      <label>
        Label <input value={label} onChange={(e) => setLabel(e.target.value)} placeholder="optional" />
      </label>
      <button className="button small primary" type="submit" disabled={create.isPending}>Save</button>
      <button className="button small link" type="button" onClick={onDone}>Cancel</button>
      {cuts.length > 0 && (
        <p className="cut-note">The window takes its time from other segments: {cuts.map((cut) => describeCut(cut, drillTypes)).join('; ')}.</p>
      )}
      {create.isError && <p role="alert" className="error">{create.error.message}</p>}
    </form>
  )
}
