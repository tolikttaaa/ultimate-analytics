package com.ttaaa.ultimate.analysis.metrics

import com.ttaaa.ultimate.analysis.inRange
import com.ttaaa.ultimate.domain.AnalysisParameters
import com.ttaaa.ultimate.domain.Sample
import com.ttaaa.ultimate.domain.TimeMetrics
import com.ttaaa.ultimate.domain.TimeRange

/**
 * Time accounting of a window (spec 6.3, 6.5): `activeSec = elapsedSec − pausedSec − gapSec`, where paused seconds are
 * samples in a pause and gap seconds have no sample. [samples] must be marked with their pauses.
 */
fun timeMetrics(samples: List<Sample>, range: TimeRange): TimeMetrics {
    val window = samples.inRange(range)
    val pausedSec = window.count { it.inPause }
    val gapSec = range.durationSec - window.size
    val activeSec = range.durationSec - pausedSec - gapSec
    return TimeMetrics(
        elapsedSec = range.durationSec,
        activeSec = activeSec,
        pausedSec = pausedSec,
        gapSec = gapSec,
        workRestRatio = if (pausedSec > 0) activeSec.toDouble() / pausedSec else null,
    )
}

/**
 * Moving speed (spec 6.3): mean smoothed speed over the samples of the window that are not in a pause, not interpolated
 * and at least `walkSpeedThreshold`, i.e. the pace without walking and standing. Null when there is no such sample.
 */
fun movingSpeed(samples: List<Sample>, range: TimeRange, params: AnalysisParameters): Double? {
    val moving = samples.inRange(range).filter { !it.inPause && !it.interpolated && it.speed >= params.walkSpeedThreshold }
    return if (moving.isEmpty()) null else moving.sumOf { it.speed } / moving.size
}
