package com.ttaaa.ultimate.analysis.smoothing

import com.ttaaa.ultimate.analysis.grid.Grid
import com.ttaaa.ultimate.domain.AnalysisParameters
import com.ttaaa.ultimate.domain.Sample

/**
 * Smooths the grid speed with a centred Savitzky–Golay filter and derives the GPS acceleration as the first derivative
 * of the same fit (spec 6.1 steps 5-6). Negative smoothed speeds are clamped to 0; the acceleration is not.
 *
 * Every run between gaps is filtered on its own. Near the ends of a run the polynomial of the first or last full
 * window is evaluated off-centre; a run shorter than `sgWindow` is fitted as a whole, with the order lowered to fit.
 * The returned samples are not yet checked for pauses: `inPause` is false.
 */
fun smooth(grid: Grid, params: AnalysisParameters): List<Sample> {
    val points = grid.points
    val speed = DoubleArray(points.size) { points[it].speed }
    val smoothed = DoubleArray(points.size)
    val accel = DoubleArray(points.size)
    val fullFilter = SavitzkyGolay(params.sgWindow, params.sgOrder)

    for (run in grid.runs()) {
        val window = minOf(params.sgWindow, run.count())
        val filter = if (window == fullFilter.size) fullFilter else SavitzkyGolay(window, minOf(params.sgOrder, window - 1))
        for (i in run) {
            val start = (i - window / 2).coerceIn(run.first, run.last - window + 1)
            smoothed[i] = filter.value(speed, start, i - start)
            accel[i] = filter.slope(speed, start, i - start)
        }
    }

    return points.mapIndexed { i, point ->
        Sample(
            t = point.t,
            timestamp = point.timestamp,
            position = point.position,
            speedRaw = point.speedRaw,
            speed = maxOf(0.0, smoothed[i]),
            accel = accel[i],
            hr = point.hr,
            distanceM = point.distanceM,
            altitudeM = point.altitudeM,
            interpolated = point.interpolated,
            inPause = false,
        )
    }
}
