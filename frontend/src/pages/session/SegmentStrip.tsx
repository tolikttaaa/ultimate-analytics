import { type MouseEvent as ReactMouseEvent, useEffect, useRef, useState } from 'react'
import {
  useDeleteSegment,
  useMergeSegments,
  useResetSegments,
  useSplitSegment,
  useUpdateSegment,
} from '../../api/segments'
import type { DrillType, Segment } from '../../api/types'
import { PLOT_MARGIN, segmentColor, segmentLabel } from '../../components/charts/sessionChartOptions'
import { duration } from '../../format'
import { edgeLimits, nextSegment, timeAt } from './strip'
import type { TimeWindow } from './timeWindow'

interface Props {
  sessionId: string
  segments: Segment[]
  drillTypes: DrillType[]
  /** The [from, to] of t the charts show, so the strip lines up with their time axis. */
  view: TimeWindow
  lastT: number
  selection: TimeWindow | null
  onSelect: (window: TimeWindow) => void
}

interface Drag {
  segmentId: string
  edge: 'start' | 'end'
  t: number
}

interface Menu {
  x: number
  y: number
  t: number
  segment: Segment | null
}

const RESET_QUESTION =
  'Replace all segments with the laps of the FIT file? Drill types and labels of the current segments are lost.'

/**
 * The segments as coloured bands above the charts (spec 10.2); uncovered time stays unlabelled (spec 5). A click
 * selects a segment's range, its edges drag to resize it, and a right click opens the segment menu.
 */
export function SegmentStrip({ sessionId, segments, drillTypes, view: [from, to], lastT, selection, onSelect }: Props) {
  const strip = useRef<HTMLDivElement>(null)
  const [drag, setDrag] = useState<Drag | null>(null)
  const [menu, setMenu] = useState<Menu | null>(null)
  const update = useUpdateSegment(sessionId)
  const remove = useDeleteSegment(sessionId)
  const split = useSplitSegment(sessionId)
  const merge = useMergeSegments(sessionId)
  const reset = useResetSegments(sessionId)
  const failed = [update, remove, split, merge, reset].find((mutation) => mutation.isError)

  const tAt = (clientX: number) => timeAt(clientX, strip.current!.getBoundingClientRect(), [from, to])
  const percent = (t: number) => `${((t - from) / (to - from)) * 100}%`
  const shown = segments.map((segment) =>
    drag?.segmentId === segment.id ? { ...segment, [drag.edge === 'start' ? 'startT' : 'endT']: drag.t } : segment)

  function startDrag(event: ReactMouseEvent, segment: Segment, edge: Drag['edge']) {
    if (event.button !== 0) return
    event.preventDefault()
    event.stopPropagation()
    const [min, max] = edgeLimits(segments, segment, edge, lastT)
    const original = edge === 'start' ? segment.startT : segment.endT
    let t = original
    const move = (moveEvent: MouseEvent) => {
      t = Math.min(max, Math.max(min, tAt(moveEvent.clientX)))
      setDrag({ segmentId: segment.id, edge, t })
    }
    const end = () => {
      window.removeEventListener('mousemove', move)
      window.removeEventListener('mouseup', end)
      if (t === original) return setDrag(null)
      update.mutate(
        { segmentId: segment.id, patch: edge === 'start' ? { startT: t } : { endT: t } },
        { onSettled: () => setDrag(null) },
      )
    }
    window.addEventListener('mousemove', move)
    window.addEventListener('mouseup', end)
  }

  function openMenu(event: ReactMouseEvent) {
    event.preventDefault()
    const t = tAt(event.clientX)
    const segment = segments.find((s) => s.startT <= t && t < s.endT) ?? null
    setMenu({ x: event.clientX, y: event.clientY, t, segment })
  }

  return (
    <>
      <div className="strip-row">
      <span className="strip-label">Segments</span>
      <div
        ref={strip}
        className="segment-strip"
        style={{ marginRight: PLOT_MARGIN.right }}
        aria-label="Segments"
        onContextMenu={openMenu}
      >
        {shown.filter((segment) => segment.endT > from && segment.startT < to).map((segment) => {
          const label = segmentLabel(segment, drillTypes)
          const selected = selection?.[0] === segment.startT && selection?.[1] === segment.endT
          return (
            <div
              key={segment.id}
              className={`segment ${selected ? 'selected' : ''}`}
              style={{
                left: percent(segment.startT),
                width: `${((segment.endT - segment.startT) / (to - from)) * 100}%`,
                background: segmentColor(segment, drillTypes),
              }}
              title={`${label}: ${duration(segment.startT)}–${duration(segment.endT)}. Click to select, drag an edge to resize, right-click for more.`}
              onClick={() => onSelect([segment.startT, segment.endT])}
            >
              <span className="segment-edge start" aria-label={`Move start of ${label}`} onMouseDown={(e) => startDrag(e, segment, 'start')} />
              <span className="segment-label">{label}</span>
              <span className="segment-edge end" aria-label={`Move end of ${label}`} onMouseDown={(e) => startDrag(e, segment, 'end')} />
            </div>
          )
        })}
      </div>
      </div>
      {failed && <p role="alert" className="error strip-error">{failed.error?.message}</p>}
      {menu && (
        <SegmentMenu
          menu={menu}
          segments={segments}
          drillTypes={drillTypes}
          onClose={() => setMenu(null)}
          onSplit={(segment, atT) => split.mutate({ segmentId: segment.id, atT })}
          onMerge={(ids) => merge.mutate(ids)}
          onType={(segment, drillTypeId) => update.mutate({ segmentId: segment.id, patch: { drillTypeId } })}
          onDelete={(segment) => remove.mutate(segment.id)}
          onReset={() => {
            if (window.confirm(RESET_QUESTION)) reset.mutate()
          }}
        />
      )}
    </>
  )
}

interface MenuProps {
  menu: Menu
  segments: Segment[]
  drillTypes: DrillType[]
  onClose: () => void
  onSplit: (segment: Segment, atT: number) => void
  onMerge: (segmentIds: string[]) => void
  onType: (segment: Segment, drillTypeId: string | null) => void
  onDelete: (segment: Segment) => void
  onReset: () => void
}

/** The context menu of the strip (spec 10.2); closes on a choice, a click elsewhere or Escape. */
function SegmentMenu({ menu, segments, drillTypes, onClose, onSplit, onMerge, onType, onDelete, onReset }: MenuProps) {
  const element = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const closeOutside = (event: MouseEvent) => {
      if (!element.current?.contains(event.target as Node)) onClose()
    }
    // Capture phase: Escape closes the menu before it would clear the selection.
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault()
        onClose()
      }
    }
    window.addEventListener('mousedown', closeOutside)
    window.addEventListener('keydown', closeOnEscape, true)
    return () => {
      window.removeEventListener('mousedown', closeOutside)
      window.removeEventListener('keydown', closeOnEscape, true)
    }
  }, [onClose])

  const choose = (action: () => void) => () => {
    onClose()
    action()
  }
  const { segment, t } = menu
  const next = segment && nextSegment(segments, segment)

  return (
    <div ref={element} className="context-menu" role="menu" style={{ left: menu.x, top: menu.y }}>
      {segment && (
        <>
          <button role="menuitem" disabled={t <= segment.startT || t >= segment.endT} onClick={choose(() => onSplit(segment, t))}>
            Split at {duration(t)}
          </button>
          <button role="menuitem" disabled={!next} onClick={choose(() => onMerge([segment.id, next!.id]))}>
            Merge with next
          </button>
          <div className="menu-group" role="group" aria-label="Drill type">
            <span className="menu-heading">Drill type</span>
            {[{ id: null, name: 'No type', color: 'transparent' }, ...drillTypes].map((type) => (
              <button
                key={type.id ?? 'none'}
                role="menuitemradio"
                aria-checked={(segment.drillTypeId ?? null) === type.id}
                onClick={choose(() => onType(segment, type.id))}
              >
                <span className="swatch" style={{ background: type.color }} />
                {type.name}
              </button>
            ))}
          </div>
          <button role="menuitem" onClick={choose(() => onDelete(segment))}>Delete</button>
          <hr />
        </>
      )}
      <button role="menuitem" onClick={choose(onReset)}>Reset from laps…</button>
    </div>
  )
}
