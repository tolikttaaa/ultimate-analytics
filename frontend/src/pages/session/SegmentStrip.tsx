import type { DrillType, Segment } from '../../api/types'
import { PLOT_MARGIN, segmentColor, segmentLabel } from '../../components/charts/sessionChartOptions'
import { duration } from '../../format'

interface Props {
  segments: Segment[]
  drillTypes: DrillType[]
  /** The [from, to] of t the charts show, so the strip lines up with their time axis. */
  view: [number, number]
}

/** The segments as coloured bands above the charts (spec 10.2); uncovered time stays unlabelled (spec 5). */
export function SegmentStrip({ segments, drillTypes, view: [from, to] }: Props) {
  const percent = (t: number) => `${((t - from) / (to - from)) * 100}%`
  return (
    <div
      className="segment-strip"
      style={{ marginLeft: PLOT_MARGIN.left, marginRight: PLOT_MARGIN.right }}
      aria-label="Segments"
    >
      {segments.filter((segment) => segment.endT > from && segment.startT < to).map((segment) => {
        const label = segmentLabel(segment, drillTypes)
        return (
          <div
            key={segment.id}
            className="segment"
            style={{
              left: percent(segment.startT),
              width: `${((segment.endT - segment.startT) / (to - from)) * 100}%`,
              background: segmentColor(segment, drillTypes),
            }}
            title={`${label}: ${duration(segment.startT)}–${duration(segment.endT)}`}
          >
            {label}
          </div>
        )
      })}
    </div>
  )
}
