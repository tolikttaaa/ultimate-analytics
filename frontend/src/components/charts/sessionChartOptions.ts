import type { EChartsCoreOption } from 'echarts/core'
import type { DrillType, Effort, Segment, SessionSeries } from '../../api/types'
import { duration, speedKmh } from '../../format'

/*
 * ECharts options of the session charts (spec 10.2): speed, GPS acceleration and heart rate over the session time t.
 * Pure functions of the API data, so they can be tested without a canvas.
 */

/** The interface font; ECharts draws on a canvas and needs it named. */
export const CHART_FONT = "Barlow, system-ui, sans-serif"

/** Text of every chart in the interface font. */
export const CHART_TEXT = {
  textStyle: { fontFamily: CHART_FONT },
}

/** Plot margins shared by the charts and the segment strip, so their time axes line up. */
export const PLOT_MARGIN = { left: 64, right: 24 }

const MPS_TO_KMH = 3.6
const COLORS = { speed: '#1565c0', recorded: '#8c959f', accel: '#6a1b9a', hr: '#c62828', rest: 'rgba(110, 118, 129, 0.15)' }
const UNTYPED_SEGMENT = '#90a4ae'

type Value = number | null | undefined

/** [t, value] points; null values make gaps in the line (spec 10.2). */
function points(t: number[], values: Value[], scale = 1): [number, number | null][] {
  return t.map((time, i) => [time, values[i] == null ? null : (values[i] as number) * scale])
}

/**
 * Recorded speed in km/h: exists only for seconds with a sample, which Smart recording writes every few seconds. The
 * line joins neighbouring samples and breaks only in gaps of the series, where the smoothed speed is null.
 */
export function recordedSpeedPoints(series: Pick<SessionSeries, 't' | 'speed' | 'speedRaw'>): [number, number | null][] {
  const result: [number, number | null][] = []
  series.t.forEach((t, i) => {
    const raw = series.speedRaw[i]
    if (series.speed[i] == null) result.push([t, null])
    else if (raw != null) result.push([t, raw * MPS_TO_KMH])
  })
  return result
}

export type Activity = 'active' | 'rest' | 'gap'

/** A run of seconds in one state, [from, to) in t. */
export interface ActivityRun {
  state: Activity
  from: number
  to: number
}

const activityOf = (inPause: boolean | null | undefined): Activity => (inPause == null ? 'gap' : inPause ? 'rest' : 'active')

/** The session as runs of active time, rest (pauses, spec 6.3) and gaps without data. */
export function activityRuns(series: Pick<SessionSeries, 't' | 'inPause'>): ActivityRun[] {
  const runs: ActivityRun[] = []
  series.t.forEach((t, i) => {
    const state = activityOf(series.inPause[i])
    const last = runs[runs.length - 1]
    if (last?.state === state) last.to = t + 1
    else runs.push({ state, from: t, to: t + 1 })
  })
  return runs
}

/** Pauses as [from, to) ranges of t; a gap ends a pause. */
export function pauseRanges(series: Pick<SessionSeries, 't' | 'inPause'>): [number, number][] {
  return activityRuns(series).filter((run) => run.state === 'rest').map((run) => [run.from, run.to])
}

/** Rest shading for a chart's markArea. */
function restAreas(series: Pick<SessionSeries, 't' | 'inPause'>) {
  return pauseRanges(series).map(([from, to]) => [{ xAxis: from, itemStyle: { color: COLORS.rest } }, { xAxis: to }])
}

/** Colour of a segment: its drill type's, or neutral when untyped. */
export function segmentColor(segment: Segment, drillTypes: DrillType[]): string {
  return drillTypes.find((type) => type.id === segment.drillTypeId)?.color ?? UNTYPED_SEGMENT
}

/** Label of a segment: its own, else its drill type's name. */
export function segmentLabel(segment: Segment, drillTypes: DrillType[]): string {
  return segment.label ?? drillTypes.find((type) => type.id === segment.drillTypeId)?.name ?? 'Segment'
}

const ACTIVITY_NAMES: Record<Activity, string> = { active: 'active', rest: 'rest', gap: 'no data' }

/** The time and its activity, then the values of this very second. */
function tooltipFormatter(series: Pick<SessionSeries, 't' | 'inPause'>, unit: string, decimals: number) {
  return (params: unknown) => {
    const items = params as { axisValue: number; seriesName: string; marker: string; value: [number, number | null] }[]
    if (items.length === 0) return ''
    // Only values of this very second: the nearest recorded sample may be seconds away.
    const t = Math.round(items[0].axisValue)
    const activity = activityOf(series.inPause[t - (series.t[0] ?? 0)])
    const lines = items
      .filter((item) => item.value[1] != null && item.value[0] === t)
      .map((item) => `${item.marker}${item.seriesName}: <b>${(item.value[1] as number).toFixed(decimals)} ${unit}</b>`)
    return [`${duration(t)}, ${ACTIVITY_NAMES[activity]}`, ...lines].join('<br/>')
  }
}

interface BaseOptions {
  series: Pick<SessionSeries, 't' | 'inPause'>
  lastT: number
  title: string
  unit: string
  decimals: number
  withSlider: boolean
}

function baseOption({ series, lastT, title, unit, decimals, withSlider }: BaseOptions): EChartsCoreOption {
  return {
    animation: false,
    ...CHART_TEXT,
    title: { text: title, left: PLOT_MARGIN.left, top: 0, textStyle: { fontFamily: CHART_FONT, fontSize: 13, fontWeight: 600, color: '#59636e' } },
    grid: { ...PLOT_MARGIN, top: 24, bottom: withSlider ? 56 : 22 },
    tooltip: { trigger: 'axis', axisPointer: { type: 'line' }, textStyle: { fontFamily: CHART_FONT }, formatter: tooltipFormatter(series, unit, decimals) },
    xAxis: {
      type: 'value',
      min: 0,
      max: lastT,
      axisLabel: { formatter: (t: number) => duration(t), hideOverlap: true },
      splitLine: { show: false },
    },
    yAxis: { type: 'value', splitNumber: 3, axisLabel: { formatter: (v: number) => `${v}` } },
    // Dragging selects a window (spec 10.2); the wheel and the slider zoom.
    brush: {
      xAxisIndex: 'all',
      brushType: 'lineX',
      brushMode: 'single',
      transformable: false,
      removeOnClick: false,
      brushStyle: { borderWidth: 1, color: 'rgba(21, 101, 192, 0.12)', borderColor: 'rgba(21, 101, 192, 0.7)' },
      outOfBrush: { colorAlpha: 1 },
    },
    dataZoom: [
      { type: 'inside', xAxisIndex: 0, filterMode: 'none' },
      ...(withSlider ? [{ type: 'slider', xAxisIndex: 0, filterMode: 'none', height: 18, bottom: 8 }] : []),
    ],
  }
}

function line(name: string, data: [number, number | null][], color: string, width = 1.5, opacity = 1) {
  return {
    name,
    type: 'line',
    data,
    color,
    showSymbol: false,
    connectNulls: false,
    // No `sampling` (spec 10.2 asks for 'lttb'): echarts.connect passes the hovered point's index in the sampled data
    // to the other charts, which sample differently, so their axis pointers stay blank. All charts share t, so
    // unsampled indices match; a 2.5 h session still draws and hovers as fast (DECISIONS.md, step 16).
    lineStyle: { width, opacity },
  }
}

export interface SpeedChartData {
  series: SessionSeries
  efforts: Effort[]
  segments: Segment[]
  drillTypes: DrillType[]
  lastT: number
}

/**
 * Smoothed speed with the recorded speed as a faint line, rest shaded grey, segments as bands in their drill type's
 * colour, and a marker at the peak of every effort (spec 10.2).
 */
export function speedChartOption({ series, efforts, segments, drillTypes, lastT }: SpeedChartData): EChartsCoreOption {
  const bands = segments.map((segment) => [
    {
      name: segmentLabel(segment, drillTypes),
      xAxis: segment.startT,
      itemStyle: { color: segmentColor(segment, drillTypes), opacity: 0.12 },
      label: { position: 'insideTopLeft', fontSize: 11, color: '#59636e' },
    },
    { xAxis: segment.endT },
  ])
  const markers = efforts.map((effort) => ({
    name: `Effort at ${duration(effort.peakT)}`,
    coord: [effort.peakT, effort.metrics.peakSpeed * MPS_TO_KMH],
    effortId: effort.id,
    peakSpeed: effort.metrics.peakSpeed,
    meanSpeedFirst3s: effort.metrics.meanSpeedFirst3s,
  }))
  return {
    ...baseOption({ series, lastT, title: 'Speed (km/h)', unit: 'km/h', decimals: 1, withSlider: false }),
    series: [
      {
        ...line('Speed', points(series.t, series.speed, MPS_TO_KMH), COLORS.speed),
        markArea: { silent: true, data: [...restAreas(series), ...bands] },
        markPoint: {
          symbol: 'circle',
          symbolSize: 9,
          itemStyle: { color: '#f57c00', borderColor: '#fff', borderWidth: 1 },
          label: { show: false },
          tooltip: {
            trigger: 'item',
            formatter: (params: { data: (typeof markers)[number] }) =>
              `${params.data.name}<br/>Peak speed: <b>${speedKmh(params.data.peakSpeed)}</b>` +
              `<br/>First 3 s: <b>${speedKmh(params.data.meanSpeedFirst3s)}</b>`,
          },
          data: markers,
        },
      },
      // Under the smoothed speed, which it would otherwise wash out.
      { ...line('Recorded speed', recordedSpeedPoints(series), COLORS.recorded, 1, 0.5), z: 1 },
    ],
  }
}

/** GPS acceleration, derived from 1 Hz speed: a comparative indicator only (spec 4.3). */
export function accelChartOption(series: SessionSeries, lastT: number): EChartsCoreOption {
  return {
    ...baseOption({ series, lastT, title: 'GPS acceleration (m/s²)', unit: 'm/s²', decimals: 2, withSlider: false }),
    series: [{ ...line('GPS acceleration', points(series.t, series.accel), COLORS.accel, 1.2), markArea: { silent: true, data: restAreas(series) } }],
  }
}

/** The visible [from, to] of t of a zoomed chart; the zoom is kept as percentages of the axis 0..lastT. */
export function zoomedRange(option: unknown, lastT: number): [number, number] {
  const zoom = (option as { dataZoom?: { start?: number; end?: number }[] }).dataZoom?.[0]
  return [((zoom?.start ?? 0) / 100) * lastT, ((zoom?.end ?? 100) / 100) * lastT]
}

/**
 * The window of a `brushEnd` event in whole seconds inside the session, or null when the brush was removed or covers
 * less than a second.
 */
export function brushedWindow(event: unknown, lastT: number): [number, number] | null {
  const range = (event as { areas?: { coordRange?: number[] }[] }).areas?.[0]?.coordRange
  if (!range || range.length < 2) return null
  const from = Math.max(0, Math.round(Math.min(range[0], range[1])))
  const to = Math.min(lastT, Math.round(Math.max(range[0], range[1])))
  return to - from >= 1 ? [from, to] : null
}

/** The brush areas showing a window on the charts. */
export function brushAreas(window: [number, number] | null) {
  return window ? [{ brushType: 'lineX', xAxisIndex: 0, coordRange: window }] : []
}

/** The effort id of a click on an effort marker, else null. */
export function clickedEffort(event: unknown): string | null {
  const click = event as { componentType?: string; data?: { effortId?: string } }
  return click.componentType === 'markPoint' ? click.data?.effortId ?? null : null
}

/** The t of an `updateAxisPointer` event, or null when the pointer left the charts. */
export function pointerTime(event: unknown): number | null {
  const value = (event as { axesInfo?: { value?: number }[] }).axesInfo?.[0]?.value
  return value == null ? null : Math.round(value)
}

/** Heart rate; carries the zoom slider of the whole group. */
export function heartRateChartOption(series: SessionSeries, lastT: number): EChartsCoreOption {
  return {
    ...baseOption({ series, lastT, title: 'Heart rate (bpm)', unit: 'bpm', decimals: 0, withSlider: true }),
    yAxis: { type: 'value', splitNumber: 3, scale: true },
    series: [{ ...line('Heart rate', points(series.t, series.hr), COLORS.hr, 1.2), markArea: { silent: true, data: restAreas(series) } }],
  }
}

/**
 * The close-up of one effort in its drawer (spec 10.2): speed and GPS acceleration from 3 s before its start to 3 s
 * after its end, with the effort shaded.
 */
export function effortChartOption(series: SessionSeries, effort: Effort): EChartsCoreOption {
  const from = Math.max(series.t[0] ?? 0, effort.startT - 3)
  const to = Math.min(series.t[series.t.length - 1] ?? 0, effort.endT + 3)
  const inRange = (data: [number, number | null][]) => data.filter(([t]) => t >= from && t <= to)
  const units: Record<string, [string, number]> = { Speed: ['km/h', 1], 'GPS acceleration': ['m/s²', 2] }
  return {
    animation: false,
    ...CHART_TEXT,
    grid: { left: 44, right: 44, top: 30, bottom: 26 },
    tooltip: {
      trigger: 'axis',
      textStyle: { fontFamily: CHART_FONT },
      formatter: (params: unknown) => {
        const items = params as { axisValue: number; seriesName: string; marker: string; value: [number, number | null] }[]
        const lines = items
          .filter((item) => item.value[1] != null)
          .map((item) => {
            const [unit, decimals] = units[item.seriesName]
            return `${item.marker}${item.seriesName}: <b>${(item.value[1] as number).toFixed(decimals)} ${unit}</b>`
          })
        return [duration(items[0]?.axisValue), ...lines].join('<br/>')
      },
    },
    xAxis: { type: 'value', min: from, max: to, minInterval: 1, axisLabel: { formatter: (t: number) => duration(t), hideOverlap: true } },
    yAxis: [
      { type: 'value', name: 'km/h', nameTextStyle: { color: COLORS.speed }, splitNumber: 3 },
      { type: 'value', name: 'm/s² (GPS)', nameTextStyle: { color: COLORS.accel }, splitNumber: 3, splitLine: { show: false } },
    ],
    series: [
      {
        ...line('Speed', inRange(points(series.t, series.speed, MPS_TO_KMH)), COLORS.speed, 2),
        showSymbol: true,
        symbolSize: 4,
        markArea: { silent: true, data: [[{ xAxis: effort.startT, itemStyle: { color: 'rgba(245, 124, 0, 0.1)' } }, { xAxis: effort.endT }]] },
      },
      { ...line('GPS acceleration', inRange(points(series.t, series.accel)), COLORS.accel, 1.5), yAxisIndex: 1 },
    ],
  }
}
