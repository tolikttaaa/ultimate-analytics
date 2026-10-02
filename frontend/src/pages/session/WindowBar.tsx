import { type FormEvent, useState } from 'react'
import { ApiError } from '../../api/client'
import { useCreateSegment } from '../../api/segments'
import type { DrillType, Segment } from '../../api/types'
import { segmentLabel } from '../../components/charts/sessionChartOptions'
import { duration } from '../../format'
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

/** Drill type and label of the new segment; an overlap (409) is explained inline. */
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

  const overlapping = segments.filter((segment) => segment.startT < to && segment.endT > from)
  const error = create.error instanceof ApiError && create.error.status === 409
    ? `The window overlaps ${overlapping.map((s) => `${segmentLabel(s, drillTypes)} (${duration(s.startT)}–${duration(s.endT)})`).join(', ') || 'another segment'}. ` +
      'Select a window between segments, or resize them on the strip.'
    : create.error?.message

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
      {error && <p role="alert" className="error">{error}</p>}
    </form>
  )
}
