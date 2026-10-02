package com.ttaaa.ultimate.domain

/**
 * One detected sprint, in Ultimate terms usually a cut (spec 3, 6.4). Derived data: produced by the analysis
 * and recomputed on every run, so it carries no identity. The id and the segment an effort belongs to are
 * added when it is stored (spec 5 invariant 3, spec 8.2).
 */
data class Effort(
    val startT: Int,
    val peakT: Int,
    val endT: Int,
    val metrics: EffortMetrics,
) {
    init {
        require(startT in 0..peakT && peakT <= endT) { "Invalid effort times: $startT, $peakT, $endT" }
    }

    val range: TimeRange get() = TimeRange(startT, endT)
}

/**
 * Per-effort metrics (spec 6.4). Units: m/s, m/s², m, s, bpm. Acceleration values are derived from 1 Hz GPS speed
 * and are comparative indicators only (spec 4.3).
 */
data class EffortMetrics(
    /** `speed[startT]`. */
    val startSpeed: Double,
    /** `speed[peakT]`. */
    val peakSpeed: Double,
    /** `peakT − startT`. */
    val timeToPeakSec: Int,
    /** `endT − startT`. */
    val durationSec: Int,
    /** Σ `speed` over `[startT, endT)` × 1 s. */
    val distanceM: Double,
    /** Mean `speed` over `[startT, endT]`. */
    val meanSpeed: Double,
    /** GPS: `(peakSpeed − startSpeed) / timeToPeakSec`. */
    val meanAccel: Double,
    /** GPS: max `accel` over `[startT, peakT]`. */
    val peakAccel: Double,
    /** `speed[startT + 1]`; null past `endT`. */
    val speedAt1s: Double?,
    /** `speed[startT + 2]`; null past `endT`. */
    val speedAt2s: Double?,
    /** `speed[startT + 3]`; null past `endT`. */
    val speedAt3s: Double?,
    /** Mean `speed` over `[startT + 1, startT + 3]`; null when those samples are missing. */
    val meanSpeedFirst3s: Double?,
    /** Σ `speed` over `[startT, startT + 3)` × 1 s. */
    val distanceFirst3s: Double,
    /** First time `speed ≥ 0.8 × peakSpeed`, interpolated between samples, minus `startT`. */
    val timeTo80PctPeakSec: Double,
    /** GPS: min `accel` over `[peakT, endT + 3]`. */
    val maxDecelAfter: Double,
    /** HR at `startT`. */
    val hrStart: Int?,
    /** Max HR over `[startT, endT + 10]`. */
    val hrMax: Int?,
)
