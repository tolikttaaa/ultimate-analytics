import { describe, expect, it } from 'vitest'
import { drillTypeStats } from '../../test/data'
import { TREND_METRICS, trendChartOption } from './drillTypeCharts'

interface Point { value: [string, number]; surface: string; itemStyle: { color: string } }
interface Option {
  title: { text: string }
  xAxis: { type: string }
  series: { data: Point[]; lineStyle: { color: string } }[]
  tooltip: { formatter: (params: { data: Point }) => string }
}

const rows = drillTypeStats([
  { id: 'a', startTime: '2026-08-19T16:06:00Z', surface: 'GRASS', peakBest: 7.5 },
  { id: 'b', startTime: '2026-09-02T16:06:00Z', surface: 'SAND', peakBest: null },
  { id: 'c', startTime: '2026-09-16T16:07:00Z', surface: 'SAND', peakBest: 7.9 },
]).sessions
const best = TREND_METRICS.find((metric) => metric.key === 'peak-best')!

describe('trendChartOption', () => {
  const option = trendChartOption(rows, best, '#e65100') as unknown as Option

  it('puts one point per session with a value on a time axis, in display units', () => {
    expect(option.xAxis.type).toBe('time')
    expect(option.title.text).toBe('Best peak speed (km/h)')
    expect(option.series[0].data.map((point) => point.value)).toEqual([['2026-08-19T16:06:00Z', 27], ['2026-09-16T16:07:00Z', 28.4]])
  })

  it('colours points by surface and the line by drill type', () => {
    expect(option.series[0].data.map((point) => point.itemStyle.color)).toEqual(['#2e7d32', '#f59e0b'])
    expect(option.series[0].lineStyle.color).toBe('#e65100')
  })

  it('explains a point on hover', () => {
    expect(option.tooltip.formatter({ data: option.series[0].data[1] }))
      .toBe('2026-09-16, sand<br/>Best peak speed: <b>28.4 km/h</b><br/>2 segments')
  })

  it('reads every metric from the API metrics', () => {
    const metrics = rows[0].metrics
    expect(TREND_METRICS.map((metric) => [metric.key, metric.value(metrics)])).toEqual([
      ['peak-best', 27],
      ['peak-mean', 6.5 * 3.6],
      ['first3s', 3.9 * 3.6],
      ['efforts-per-min', 0.84],
      ['moving-speed', 3.9 * 3.6],
      ['active-min', 1857 / 60],
      ['hr-avg', 148.6],
    ])
  })

  it('keeps a single day in a month-long axis instead of years', () => {
    const one = trendChartOption(rows.slice(0, 1), best, '#e65100') as unknown as { xAxis: { min: number; max: number } }
    expect(one.xAxis.max - one.xAxis.min).toBe(30 * 86_400_000)
    expect((option as unknown as { xAxis: { min?: number } }).xAxis.min).toBeUndefined()
  })
})
