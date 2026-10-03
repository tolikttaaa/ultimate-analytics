import { useTheme } from '../../theme'
import { CHART_PALETTES, type ChartPalette } from './sessionChartOptions'

/** The chart colours of the current theme. */
export function useChartPalette(): ChartPalette {
  return CHART_PALETTES[useTheme().theme]
}
