package com.ttaaa.ultimate.analysis.grid

import com.ttaaa.ultimate.analysis.consecutiveRuns
import com.ttaaa.ultimate.domain.GeoPoint
import com.ttaaa.ultimate.domain.RecordingMode
import java.time.Instant

/**
 * The 1 Hz grid of a session after gap handling and outlier removal (spec 6.1 steps 2-4), before smoothing.
 * [points] are ordered by `t`; a second without a point is a gap.
 */
data class Grid(
    /** Time of the first record (`t = 0`). */
    val startTime: Instant,
    /** `t` of the last record: the session's time axis is `[0, lastT]`. */
    val lastT: Int,
    val recordingMode: RecordingMode,
    val points: List<GridPoint>,
) {
    /** Maximal runs of consecutive seconds, as index ranges into [points]. Nothing may span two runs (spec 6.1). */
    fun runs(): List<IntRange> = consecutiveRuns(points) { it.t }
}

/** One second of the [Grid]. */
data class GridPoint(
    val t: Int,
    val timestamp: Instant,
    val position: GeoPoint?,
    /** Speed recorded by the watch at this second, including a removed outlier; null if none was recorded. */
    val speedRaw: Double?,
    /** Recorded or interpolated speed without outliers, m/s: the input of the smoothing. */
    val speed: Double,
    val hr: Int?,
    val distanceM: Double?,
    val altitudeM: Double?,
    /** The speed of this second is interpolated: no plausible speed was recorded for it. */
    val interpolated: Boolean,
)
