import type { EChartsCoreOption } from 'echarts/core'
import type { Schemas, Surface, WindowMetrics } from '../../api/types'
import { CHART_FONT, CHART_TEXT } from './sessionChartOptions'

type SessionRow = Schemas['DrillTypeSessionRow']

/** A per-session value of the drill type trend (spec 10.1): read from the API's metrics, converted for display. */
export interface TrendMetric {
  key: string
  label: string
  unit: string
  decimals: number
  value: (metrics: WindowMetrics) => number | null | undefined
}

const KMH = 3.6

export const TREND_METRICS: TrendMetric[] = [
  { key: 'peak-best', label: 'Best peak speed', unit: 'km/h', decimals: 1, value: (m) => m.efforts.peakSpeed && m.efforts.peakSpeed.best * KMH },
  { key: 'peak-mean', label: 'Mean peak speed', unit: 'km/h', decimals: 1, value: (m) => m.efforts.peakSpeed && m.efforts.peakSpeed.mean * KMH },
  { key: 'first3s', label: 'Mean first 3 s speed', unit: 'km/h', decimals: 1, value: (m) => m.efforts.meanSpeedFirst3s && m.efforts.meanSpeedFirst3s.mean * KMH },
  { key: 'efforts-per-min', label: 'Efforts per active minute', unit: '/min', decimals: 2, value: (m) => m.efforts.perActiveMin },
  { key: 'moving-speed', label: 'Moving speed', unit: 'km/h', decimals: 1, value: (m) => m.distance.movingSpeed == null ? null : m.distance.movingSpeed * KMH },
  { key: 'active-min', label: 'Active time', unit: 'min', decimals: 1, value: (m) => m.time.activeSec / 60 },
  { key: 'hr-avg', label: 'Average heart rate', unit: 'bpm', decimals: 0, value: (m) => m.heartRate.avg },
]

/* As the surface chips. */
const SURFACE_COLORS: Record<Surface, string> = { GRASS: '#2e7d32', SAND: '#f59e0b', UNKNOWN: '#8c959f' }
const SURFACE_NAMES: Record<Surface, string> = { GRASS: 'Grass', SAND: 'Sand', UNKNOWN: 'Unknown surface' }

const day = (iso: string) => iso.slice(0, 10)

/**
 * One point per session in time, coloured by its surface, joined by a line in the drill type's colour. Sessions
 * without a value (e.g. no efforts) are left out.
 */
export function trendChartOption(rows: SessionRow[], metric: TrendMetric, color: string): EChartsCoreOption {
  const points = rows.flatMap((row) => {
    const value = metric.value(row.metrics)
    return value == null ? [] : [{
      value: [row.startTime, Number(value.toFixed(metric.decimals))],
      surface: row.surface,
      segments: row.segmentCount,
      itemStyle: { color: SURFACE_COLORS[row.surface] },
    }]
  })
  // With one day only, ECharts would stretch the axis over years: a month around it instead.
  const times = points.map((point) => Date.parse(point.value[0] as string))
  const oneDay = times.length > 0 && Math.max(...times) - Math.min(...times) < 86_400_000
  const range = oneDay ? { min: Math.min(...times) - 15 * 86_400_000, max: Math.max(...times) + 15 * 86_400_000 } : {}
  return {
    animation: false,
    ...CHART_TEXT,
    grid: { left: 56, right: 24, top: 36, bottom: 32 },
    title: { text: `${metric.label} (${metric.unit})`, left: 56, top: 4, textStyle: { fontFamily: CHART_FONT, fontSize: 13, fontWeight: 600, color: '#59636e' } },
    tooltip: {
      trigger: 'item',
      textStyle: { fontFamily: CHART_FONT },
      formatter: (params: { data: (typeof points)[number] }) => {
        const { value, surface, segments } = params.data
        return `${day(value[0] as string)}, ${SURFACE_NAMES[surface].toLowerCase()}<br/>${metric.label}: <b>${value[1]} ${metric.unit}</b>` +
          `<br/>${segments} segment${segments === 1 ? '' : 's'}`
      },
    },
    // Padding keeps the points off the plot edges.
    xAxis: { type: 'time', ...range, boundaryGap: ['4%', '4%'], axisLabel: { formatter: '{yyyy}-{MM}-{dd}', hideOverlap: true } },
    yAxis: { type: 'value', scale: true, splitNumber: 4, boundaryGap: ['10%', '10%'] },
    series: [{
      name: metric.label,
      type: 'line',
      data: points,
      symbol: 'circle',
      symbolSize: 9,
      lineStyle: { color, width: 1.5, opacity: 0.6 },
    }],
  }
}
