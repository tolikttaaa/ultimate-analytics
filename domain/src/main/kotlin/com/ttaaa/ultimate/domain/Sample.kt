package com.ttaaa.ultimate.domain

import java.time.Instant

/**
 * One row of the 1 Hz series (spec 3, 5). A session's samples are ordered by [t]. Gaps longer than
 * `maxInterpolationGapSec` and timer-stop intervals have no samples: a missing `t` is a gap (spec 6.1).
 */
data class Sample(
    /** Seconds from the first sample. */
    val t: Int,
    val timestamp: Instant,
    val position: GeoPoint?,
    /** Speed recorded by the watch (`enhanced_speed`, fallback `speed`), m/s; null if not recorded. */
    val speedRaw: Double?,
    /** Smoothed speed, m/s, never negative (spec 6.1). */
    val speed: Double,
    /** GPS acceleration: derivative of the smoothed speed, m/s² (spec 4.3, 6.1). */
    val accel: Double,
    /** Heart rate, bpm. */
    val hr: Int?,
    /** Cumulative distance recorded by the watch, m. */
    val distanceM: Double?,
    val altitudeM: Double?,
    /** Filled by interpolation across a short gap or a removed outlier (spec 6.1). */
    val interpolated: Boolean,
    val inPause: Boolean,
) {
    init {
        require(t >= 0) { "Negative sample time: $t" }
    }
}
