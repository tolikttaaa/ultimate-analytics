import { LineChart } from 'echarts/charts'
import {
  BrushComponent,
  DataZoomComponent,
  GridComponent,
  MarkAreaComponent,
  MarkPointComponent,
  TitleComponent,
  TooltipComponent,
} from 'echarts/components'
import * as echarts from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { useEffect, useLayoutEffect, useRef } from 'react'

// Only the parts the charts use, to keep the bundle small.
echarts.use([LineChart, GridComponent, TooltipComponent, DataZoomComponent, MarkAreaComponent, MarkPointComponent,
  TitleComponent, BrushComponent, CanvasRenderer])

export type ChartEventHandlers = Record<string, (params: unknown, chart: echarts.ECharts) => void>

interface Props {
  option: echarts.EChartsCoreOption
  height: number
  /** Charts of one group share tooltip, axis pointer and zoom (`echarts.connect`, spec 10.2). */
  group?: string
  /** ECharts events by name, e.g. `datazoom`; the handlers may change, the set of names should not. */
  onEvents?: ChartEventHandlers
  /** Dragging draws the option's brush instead of panning. */
  brush?: boolean
  /** The chart once created, and null when it is gone. */
  onChart?: (chart: echarts.ECharts | null) => void
}

/**
 * A chart that follows its option. New options are merged, so the zoom survives data changes; the option should be
 * memoised all the same.
 */
export function EChart({ option, height, group, onEvents, brush = false, onChart }: Props) {
  const container = useRef<HTMLDivElement>(null)
  const chart = useRef<echarts.ECharts | null>(null)
  const handlers = useRef(onEvents)
  const chartListener = useRef(onChart)
  const eventNames = Object.keys(onEvents ?? {}).sort().join(',')

  useLayoutEffect(() => {
    handlers.current = onEvents
    chartListener.current = onChart
  })

  useEffect(() => {
    const element = container.current!
    const instance = echarts.init(element)
    chart.current = instance
    if (group) {
      instance.group = group
      echarts.connect(group)
    }
    for (const name of eventNames ? eventNames.split(',') : []) {
      instance.on(name, (params) => handlers.current?.[name]?.(params, instance))
    }
    const resize = new ResizeObserver(() => instance.resize())
    resize.observe(element)
    chartListener.current?.(instance)
    return () => {
      chartListener.current?.(null)
      resize.disconnect()
      instance.dispose()
      chart.current = null
    }
  }, [group, eventNames])

  useEffect(() => {
    const instance = chart.current
    if (!instance) return
    instance.setOption(option)
    if (brush) {
      instance.dispatchAction({ type: 'takeGlobalCursor', key: 'brush', brushOption: { brushType: 'lineX', brushMode: 'single' } })
    }
  }, [option, group, eventNames, brush])

  return <div ref={container} className="echart" style={{ height }} />
}
