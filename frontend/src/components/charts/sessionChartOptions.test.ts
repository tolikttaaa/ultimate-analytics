import { describe, expect, it } from 'vitest'
import { drillType, effort, segment, sessionSeries } from '../../test/data'
import {
  accelChartOption,
  heartRateChartOption,
  pauseRanges,
  recordedSpeedPoints,
  segmentColor,
  segmentLabel,
  speedChartOption,
  zoomedRange,
} from './sessionChartOptions'

/* The parts of an ECharts option the tests read. */
interface MarkAreaItem {
  xAxis: number
  name?: string
  itemStyle?: { color: string }
}
interface Series {
  name: string
  data: [number, number | null][]
  connectNulls: boolean
  sampling?: string
  markArea?: { data: [MarkAreaItem, MarkAreaItem][] }
  markPoint?: {
    data: { coord: [number, number]; effortId: string }[]
    tooltip: { formatter: (params: unknown) => string }
  }
}
interface Option {
  series: Series[]
  xAxis: { min: number; max: number }
  dataZoom: { type: string }[]
  tooltip: { formatter: (params: unknown) => string }
}

const asOption = (option: unknown) => option as Option

describe('pauseRanges', () => {
  it('turns runs of paused seconds into [from, to) ranges; a gap ends a run', () => {
    const series = { t: [0, 1, 2, 3, 4, 5, 6], inPause: [false, true, true, false, true, null, true] }
    expect(pauseRanges(series)).toEqual([[1, 3], [4, 5], [6, 7]])
  })

  it('is empty without pauses', () => {
    expect(pauseRanges({ t: [0, 1], inPause: [false, false] })).toEqual([])
  })
})

describe('segments', () => {
  const types = [drillType()]

  it('take colour and name from their drill type, label first', () => {
    expect(segmentColor(segment(), types)).toBe('#e65100')
    expect(segmentLabel(segment(), types)).toBe('Sprints')
    expect(segmentLabel(segment({ label: '5 x 30 m' }), types)).toBe('5 x 30 m')
  })

  it('are neutral when untyped', () => {
    const untyped = segment({ drillTypeId: null })
    expect(segmentColor(untyped, types)).toBe('#90a4ae')
    expect(segmentLabel(untyped, types)).toBe('Segment')
  })
})

describe('speedChartOption', () => {
  const option = asOption(speedChartOption({
    series: sessionSeries(),
    efforts: [effort()],
    segments: [segment()],
    drillTypes: [drillType()],
    lastT: 9,
  }))
  const [speed, recorded] = option.series

  it('plots smoothed and recorded speed in km/h with gaps as breaks', () => {
    expect(speed.name).toBe('Speed')
    expect(speed.data[3]).toEqual([3, 21.6])
    expect(speed.data[8]).toEqual([8, null])
    expect(speed.connectNulls).toBe(false)
    // Sampled data would break the axis pointers of the connected charts.
    expect(speed.sampling).toBeUndefined()
    expect(recorded.name).toBe('Recorded speed')
    expect(recorded.data[3][1]).toBeCloseTo(22.32)
    expect(option.xAxis).toMatchObject({ min: 0, max: 9 })
  })

  it('shades pauses and colours segments by drill type', () => {
    const areas = speed.markArea!.data.map(([from, to]) => [from.xAxis, to.xAxis, from.name, from.itemStyle?.color])
    expect(areas).toEqual([
      [6, 8, undefined, 'rgba(31, 35, 40, 0.07)'],
      [0, 6, 'Sprints', '#e65100'],
    ])
  })

  it('marks every effort at its peak and explains it on hover', () => {
    const [marker] = speed.markPoint!.data
    expect(marker.coord).toEqual([3, 21.6])
    expect(marker.effortId).toBe('e1')
    expect(speed.markPoint!.tooltip.formatter({ data: marker })).toBe(
      'Effort at 0:03<br/>Peak speed: <b>21.6 km/h</b><br/>First 3 s: <b>18.0 km/h</b>',
    )
  })

  it('lists the values of the cursor second, skipping gaps and samples of other seconds', () => {
    const at = (seriesName: string, t: number, value: number | null) => ({ axisValue: 125, seriesName, marker: '•', value: [t, value] })
    expect(option.tooltip.formatter([at('Speed', 125, 21.64), at('Recorded speed', 125, 22.1)]))
      .toBe('2:05<br/>•Speed: <b>21.6 km/h</b><br/>•Recorded speed: <b>22.1 km/h</b>')
    expect(option.tooltip.formatter([at('Speed', 125, 21.64), at('Recorded speed', 122, 20)]))
      .toBe('2:05<br/>•Speed: <b>21.6 km/h</b>')
    expect(option.tooltip.formatter([at('Speed', 125, null)])).toBe('2:05')
  })
})

describe('recordedSpeedPoints', () => {
  it('joins the recorded samples and breaks only in gaps', () => {
    const series = { t: [0, 1, 2, 3, 4, 5], speed: [1, 1, 1, null, 1, 1], speedRaw: [1, null, 2, null, null, 3] }
    expect(recordedSpeedPoints(series)).toEqual([[0, 3.6], [2, 7.2], [3, null], [5, 10.8]])
  })
})

describe('acceleration and heart rate charts', () => {
  it('plot their series on the same time axis', () => {
    const accel = asOption(accelChartOption(sessionSeries(), 9))
    const heartRate = asOption(heartRateChartOption(sessionSeries(), 9))
    expect(accel.series[0].data[1]).toEqual([1, 2])
    expect(heartRate.series[0].data[8]).toEqual([8, null])
    expect(accel.xAxis.max).toBe(9)
    expect(heartRate.xAxis.max).toBe(9)
  })

  it('put the zoom slider under the bottom chart only', () => {
    expect(asOption(accelChartOption(sessionSeries(), 9)).dataZoom.map((zoom) => zoom.type)).toEqual(['inside'])
    expect(asOption(heartRateChartOption(sessionSeries(), 9)).dataZoom.map((zoom) => zoom.type)).toEqual(['inside', 'slider'])
  })
})

describe('zoomedRange', () => {
  it('converts the zoom percentages to t', () => {
    expect(zoomedRange({ dataZoom: [{ start: 25, end: 50 }] }, 1200)).toEqual([300, 600])
    expect(zoomedRange({}, 1200)).toEqual([0, 1200])
  })
})
