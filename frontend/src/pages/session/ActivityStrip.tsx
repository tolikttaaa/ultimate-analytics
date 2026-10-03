import { useMemo } from 'react'
import type { SessionSeries } from '../../api/types'
import { type Activity, activityRuns, PLOT_MARGIN } from '../../components/charts/sessionChartOptions'
import { duration } from '../../format'
import { stripPosition } from './strip'
import type { TimeWindow } from './timeWindow'

interface Props {
  series: Pick<SessionSeries, 't' | 'inPause'>
  /** The [from, to] of t the charts show, so the band lines up with their time axis. */
  view: TimeWindow
  onSelect: (window: TimeWindow) => void
}

const NAMES: Record<Activity, string> = { active: 'Active', rest: 'Rest', gap: 'No data' }

/**
 * Active time and rest (pauses, spec 6.3) as a band under the segment strip, following the charts' zoom. A click
 * selects a run as the window.
 */
export function ActivityStrip({ series, view: [from, to], onSelect }: Props) {
  const runs = useMemo(() => activityRuns(series), [series])
  return (
    <div className="strip-row">
      <span className="strip-label">Activity</span>
      <div className="activity-strip" style={{ marginRight: PLOT_MARGIN.right }} aria-label="Activity">
        {runs.filter((run) => run.to > from && run.from < to).map((run) => (
          <div
            key={run.from}
            className={`activity-run ${run.state}`}
            style={stripPosition([run.from, run.to], [from, to])}
            title={`${NAMES[run.state]} ${duration(run.from)}–${duration(run.to)} (${duration(run.to - run.from)}). Click to select.`}
            onClick={() => onSelect([run.from, run.to])}
          />
        ))}
      </div>
      <span className="activity-legend" aria-hidden="true">
        <span className="legend-swatch active" />Active
        <span className="legend-swatch rest" />Rest
      </span>
    </div>
  )
}
