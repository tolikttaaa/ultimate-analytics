import { useMemo } from 'react'
import { type ChartEventHandlers, EChart } from './EChart'
import {
  accelChartOption,
  heartRateChartOption,
  type SpeedChartData,
  speedChartOption,
  zoomedRange,
} from './sessionChartOptions'

interface Props extends SpeedChartData {
  /** Called with the visible [from, to] of t whenever the charts are zoomed. */
  onZoom?: (range: [number, number]) => void
}

/** The three synchronised charts of the session screen (spec 10.2). */
export function SessionCharts({ series, efforts, segments, drillTypes, lastT, onZoom }: Props) {
  const speed = useMemo(
    () => speedChartOption({ series, efforts, segments, drillTypes, lastT }),
    [series, efforts, segments, drillTypes, lastT],
  )
  const accel = useMemo(() => accelChartOption(series, lastT), [series, lastT])
  const heartRate = useMemo(() => heartRateChartOption(series, lastT), [series, lastT])
  // Connected charts repeat every zoom action, so listening on one of them is enough.
  const speedEvents = useMemo<ChartEventHandlers>(() => ({
    datazoom: (_, chart) => onZoom?.(zoomedRange(chart.getOption(), lastT)),
  }), [onZoom, lastT])

  return (
    <div className="session-charts">
      <EChart option={speed} height={280} group="session" onEvents={speedEvents} />
      <EChart option={accel} height={170} group="session" />
      <EChart option={heartRate} height={210} group="session" />
    </div>
  )
}
