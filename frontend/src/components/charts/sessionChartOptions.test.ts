import { describe, expect, it } from 'vitest'
import { drillType, effort, segment, sessionSeries } from '../../test/data'
import {
  accelChartOption,
  activityRuns,
  brushAreas,
  brushedWindow,
  clickedEffort,
  effortChartOption,
  heartRateChartOption,
  pauseRanges,
  pointerTime,
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

describe('activityRuns', () => {
  it('splits the session into active, rest and gap runs', () => {
    expect(activityRuns(sessionSeries())).toEqual([
      { state: 'active', from: 0, to: 6 },
      { state: 'rest', from: 6, to: 8 },
      { state: 'gap', from: 8, to: 9 },
      { state: 'active', from: 9, to: 10 },
    ])
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
      [6, 8, undefined, 'rgba(110, 118, 129, 0.15)'],
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

  it('names the activity of the cursor second and lists its values, skipping gaps and other seconds', () => {
    const at = (t: number, seriesName: string, sampleT: number, value: number | null) => ({ axisValue: t, seriesName, marker: '•', value: [sampleT, value] })
    expect(option.tooltip.formatter([at(3, 'Speed', 3, 21.6), at(3, 'Recorded speed', 3, 22.32)]))
      .toBe('0:03, active<br/>•Speed: <b>21.6 km/h</b><br/>•Recorded speed: <b>22.3 km/h</b>')
    expect(option.tooltip.formatter([at(6, 'Speed', 6, 0), at(6, 'Recorded speed', 5, 7.6)])).toBe('0:06, rest<br/>•Speed: <b>0.0 km/h</b>')
    expect(option.tooltip.formatter([at(8, 'Speed', 8, null)])).toBe('0:08, no data')
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

  it('shade rest like the speed chart', () => {
    for (const option of [accelChartOption(sessionSeries(), 9), heartRateChartOption(sessionSeries(), 9)]) {
      const areas = asOption(option).series[0].markArea!.data.map(([from, to]) => [from.xAxis, to.xAxis])
      expect(areas).toEqual([[6, 8]])
    }
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

describe('pointerTime', () => {
  it('reads t from an axis pointer event, or null when the pointer left', () => {
    expect(pointerTime({ axesInfo: [{ axisDim: 'x', axisIndex: 0, value: 754.6 }] })).toBe(755)
    expect(pointerTime({ axesInfo: [] })).toBeNull()
  })
})

describe('window selection', () => {
  it('brushes along the time axis of every chart without panning', () => {
    const option = accelChartOption(sessionSeries(), 9) as { brush: Record<string, unknown> }
    expect(option.brush).toMatchObject({ xAxisIndex: 'all', brushType: 'lineX', brushMode: 'single', removeOnClick: false })
  })

  it('turns a brush into whole seconds inside the session', () => {
    expect(brushedWindow({ areas: [{ coordRange: [600.4, 1199.6] }] }, 1800)).toEqual([600, 1200])
    expect(brushedWindow({ areas: [{ coordRange: [1700, -20] }] }, 1500)).toEqual([0, 1500])
  })

  it('ignores a removed or too short brush', () => {
    expect(brushedWindow({ areas: [] }, 1800)).toBeNull()
    expect(brushedWindow({ areas: [{ coordRange: [600.2, 600.4] }] }, 1800)).toBeNull()
  })

  it('draws a window as a brush area, and none as no areas', () => {
    expect(brushAreas([600, 1200])).toEqual([{ brushType: 'lineX', xAxisIndex: 0, coordRange: [600, 1200] }])
    expect(brushAreas(null)).toEqual([])
  })
})

describe('clickedEffort', () => {
  it('reads the effort of a clicked marker only', () => {
    expect(clickedEffort({ componentType: 'markPoint', data: { effortId: 'e1' } })).toBe('e1')
    expect(clickedEffort({ componentType: 'series', data: [3, 21] })).toBeNull()
  })
})

describe('effortChartOption', () => {
  const option = effortChartOption(sessionSeries(), effort({ startT: 4, endT: 5 })) as unknown as Option & {
    yAxis: { name: string }[]
    series: (Series & { yAxisIndex?: number })[]
  }

  it('shows 3 s around the effort, clipped to the session', () => {
    expect(option.xAxis).toMatchObject({ min: 1, max: 8 })
    expect(option.series[0].data.map(([t]) => t)).toEqual([1, 2, 3, 4, 5, 6, 7, 8])
    expect(option.series[0].data[2]).toEqual([3, 21.6])
  })

  it('plots GPS acceleration on its own axis and shades the effort', () => {
    expect(option.yAxis.map((axis) => axis.name)).toEqual(['km/h', 'm/s² (GPS)'])
    expect(option.series[1]).toMatchObject({ name: 'GPS acceleration', yAxisIndex: 1 })
    expect(option.series[0].markArea!.data[0].map((item) => item.xAxis)).toEqual([4, 5])
  })

  it('gives each value its unit in the tooltip', () => {
    expect(option.tooltip.formatter([
      { axisValue: 4, seriesName: 'Speed', marker: '•', value: [4, 14.4] },
      { axisValue: 4, seriesName: 'GPS acceleration', marker: '•', value: [4, -2] },
    ])).toBe('0:04<br/>•Speed: <b>14.4 km/h</b><br/>•GPS acceleration: <b>-2.00 m/s²</b>')
  })
})
