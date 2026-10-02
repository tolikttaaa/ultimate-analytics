import type { ECharts } from 'echarts/core'
import { useEffect, useMemo, useRef } from 'react'
import { type ChartEventHandlers, EChart } from './EChart'
import {
  accelChartOption,
  brushAreas,
  brushedWindow,
  clickedEffort,
  heartRateChartOption,
  pointerTime,
  type SpeedChartData,
  speedChartOption,
  zoomedRange,
} from './sessionChartOptions'

interface Props extends SpeedChartData {
  /** The selected window, drawn as a brush on all charts. */
  selection: [number, number] | null
  /** Called with the window the user brushed. */
  onSelect: (window: [number, number] | null) => void
  /** Called with the id of a clicked effort marker. */
  onEffortClick: (effortId: string) => void
  /** Called with the visible [from, to] of t whenever the charts are zoomed. */
  onZoom?: (range: [number, number]) => void
  /** Called with the t under the axis pointer of any chart, and null when it leaves them. */
  onCursor?: (t: number | null) => void
}

/** The three synchronised charts of the session screen (spec 10.2). */
export function SessionCharts(props: Props) {
  const { series, efforts, segments, drillTypes, lastT, selection, onSelect, onEffortClick, onZoom, onCursor } = props
  const speedChart = useRef<ECharts | null>(null)
  const speed = useMemo(
    () => speedChartOption({ series, efforts, segments, drillTypes, lastT }),
    [series, efforts, segments, drillTypes, lastT],
  )
  const accel = useMemo(() => accelChartOption(series, lastT), [series, lastT])
  const heartRate = useMemo(() => heartRateChartOption(series, lastT), [series, lastT])
  // Connected charts repeat every zoom, axis pointer and brush action, so listening on one of them is enough.
  const speedEvents = useMemo<ChartEventHandlers>(() => ({
    datazoom: (_, chart) => onZoom?.(zoomedRange(chart.getOption(), lastT)),
    updateAxisPointer: (event) => onCursor?.(pointerTime(event)),
    brushEnd: (event) => onSelect(brushedWindow(event, lastT)),
    click: (event) => {
      const effortId = clickedEffort(event)
      if (effortId) onEffortClick(effortId)
    },
  }), [onZoom, onCursor, onSelect, onEffortClick, lastT])

  // The selection also changes from the URL, the segment strip and Escape: draw it.
  const [from, to] = selection ?? [null, null]
  useEffect(() => {
    speedChart.current?.dispatchAction({ type: 'brush', areas: brushAreas(from === null ? null : [from, to!]) })
  }, [from, to, speed])

  return (
    <div className="session-charts">
      <EChart option={speed} height={280} group="session" onEvents={speedEvents} brush onChart={(chart) => (speedChart.current = chart)} />
      <EChart option={accel} height={170} group="session" brush />
      <EChart option={heartRate} height={210} group="session" brush />
    </div>
  )
}
