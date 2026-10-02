package com.ttaaa.ultimate.domain

/**
 * Metrics of a time window (spec 6.5); the same shape serves a session, a segment, a brushed window and
 * drill-type totals (spec 9.2). Units: m, s, m/s, m/s², bpm. Values that need data the window does not have
 * (no moving samples, no efforts, no heart rate) are null.
 */
data class WindowMetrics(
    val range: TimeRange,
    val time: TimeMetrics,
    val distance: DistanceMetrics,
    /** One entry per [SpeedZone], in zone order. */
    val zones: List<SpeedZoneMetrics>,
    val efforts: EffortStats,
    /** Runs of `accel ≤ decelThreshold` lasting at least 1 s. */
    val decelCount: Int,
    val fatigue: FatigueMetrics,
    val heartRate: HeartRateMetrics,
)

data class TimeMetrics(
    val elapsedSec: Int,
    /** Elapsed time minus pause and gap time (spec 6.3). */
    val activeSec: Int,
    val pausedSec: Int,
    val gapSec: Int,
    /** `activeSec / pausedSec`; null without pauses. */
    val workRestRatio: Double?,
)

data class DistanceMetrics(
    val distanceM: Double,
    val activeDistanceM: Double,
    /** Mean speed while moving, excluding pauses, interpolated samples and walking (spec 6.3). */
    val movingSpeed: Double?,
    /** Pace at [movingSpeed], s/km. */
    val movingPaceSecPerKm: Double?,
    val maxSpeed: Double?,
)

/** Speed zones bounded by `AnalysisParameters.speedZones` (upper bounds, spec 6.2). */
enum class SpeedZone { STAND, WALK, JOG, RUN, HIGH_SPEED, SPRINT }

data class SpeedZoneMetrics(val zone: SpeedZone, val timeSec: Int, val distanceM: Double)

/** Effort statistics over the efforts that start inside the window (spec 6.5). */
data class EffortStats(
    val count: Int,
    val perActiveMin: Double?,
    val peakSpeed: MeanAndBest?,
    val meanSpeed: MeanAndBest?,
    val meanAccel: MeanAndBest?,
    val peakAccel: MeanAndBest?,
    val meanSpeedFirst3s: MeanAndBest?,
    /** Lower is better, so [MeanAndBest.best] is the minimum. */
    val timeTo80PctPeakSec: MeanAndBest?,
)

/** Mean and best value of one effort metric; best is the maximum unless the metric says otherwise. */
data class MeanAndBest(val mean: Double, val best: Double)

data class FatigueMetrics(
    /**
     * `(mean peakSpeed of the first third of efforts − mean of the last third) / first-third mean × 100`;
     * null below 6 efforts.
     */
    val peakSpeedDropPct: Double?,
)

data class HeartRateMetrics(
    val avg: Double?,
    val max: Int?,
    /** Time in the 5 heart-rate zones (50/60/70/80/90 % of `hrMax`) as % of the window; empty without HR data. */
    val zonesPct: List<Double>,
)
