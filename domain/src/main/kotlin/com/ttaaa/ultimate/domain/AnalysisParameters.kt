package com.ttaaa.ultimate.domain

/**
 * Every threshold of the analysis pipeline, with its default (spec 6.2). Defaults are provisional until calibrated
 * on real files (milestone M0).
 *
 * Changing any default changes analysis results: bump `ANALYSIS_VERSION` in the `analysis` module together with it.
 */
data class AnalysisParameters(
    /** Longest gap filled by linear interpolation, s. Longer gaps stay gaps (spec 6.1). */
    val maxInterpolationGapSec: Int = 3,
    /** Speeds above this are outliers, set to missing and re-interpolated, m/s (spec 6.1). */
    val maxPlausibleSpeed: Double = 11.0,
    /** Savitzky–Golay window, samples; odd (spec 6.1). */
    val sgWindow: Int = 5,
    /** Savitzky–Golay polynomial order; at least 1 so that acceleration is defined (spec 6.1). */
    val sgOrder: Int = 2,
    /** Samples below this speed are walking and do not count towards moving speed, m/s (spec 6.3). */
    val walkSpeedThreshold: Double = 2.0,
    /** A pause is a run of samples below this speed, m/s (spec 6.3). */
    val pauseSpeedThreshold: Double = 1.5,
    /** Minimum length of a pause, s (spec 6.3). */
    val minPauseDurationSec: Int = 20,
    /** Excursions above [pauseSpeedThreshold] up to this length do not break a pause, s (spec 6.3). */
    val pauseSpikeToleranceSec: Int = 2,
    /** An effort is triggered when acceleration reaches this, m/s² (spec 6.4). */
    val effortStartAccel: Double = 1.0,
    /** Minimum peak speed of an effort, m/s (spec 6.4). */
    val effortMinPeakSpeed: Double = 4.5,
    /** Minimum gain from start to peak speed, m/s (spec 6.4). */
    val effortMinSpeedGain: Double = 2.0,
    /** An effort ends when speed drops below this share of its peak speed (spec 6.4). */
    val effortEndPeakRatio: Double = 0.7,
    /** ...or below this speed, whichever is higher, m/s (spec 6.4). */
    val effortEndMinSpeed: Double = 2.5,
    /** Minimum effort duration, s (spec 6.4). */
    val effortMinDurationSec: Int = 2,
    /** Efforts are cut at this duration, s (spec 6.4). */
    val effortMaxDurationSec: Int = 15,
    /** Acceleration at or below this counts as a deceleration, m/s² (spec 6.5). */
    val decelThreshold: Double = -1.5,
    /** Upper bounds of the speed zones STAND, WALK, JOG, RUN and HIGH_SPEED; SPRINT is above the last, m/s. */
    val speedZones: List<Double> = listOf(1.0, 2.0, 4.0, 5.5, 7.0),
    /** Maximum heart rate for the HR zones, bpm (user setting). */
    val hrMax: Int = 190,
) {
    init {
        require(maxInterpolationGapSec >= 0) { "maxInterpolationGapSec must not be negative" }
        require(maxPlausibleSpeed > 0.0) { "maxPlausibleSpeed must be positive" }
        require(sgWindow >= 3 && sgWindow % 2 == 1) { "sgWindow must be odd and at least 3" }
        require(sgOrder in 1..<sgWindow) { "sgOrder must be at least 1 and below sgWindow" }
        require(walkSpeedThreshold > 0.0) { "walkSpeedThreshold must be positive" }
        require(pauseSpeedThreshold > 0.0) { "pauseSpeedThreshold must be positive" }
        require(minPauseDurationSec > 0) { "minPauseDurationSec must be positive" }
        require(pauseSpikeToleranceSec >= 0) { "pauseSpikeToleranceSec must not be negative" }
        require(effortStartAccel > 0.0) { "effortStartAccel must be positive" }
        require(effortMinPeakSpeed > 0.0) { "effortMinPeakSpeed must be positive" }
        require(effortMinSpeedGain > 0.0) { "effortMinSpeedGain must be positive" }
        require(effortEndPeakRatio > 0.0 && effortEndPeakRatio < 1.0) { "effortEndPeakRatio must be in (0, 1)" }
        require(effortEndMinSpeed >= 0.0) { "effortEndMinSpeed must not be negative" }
        require(effortMinDurationSec in 1..effortMaxDurationSec) {
            "effortMinDurationSec must be at least 1 and at most effortMaxDurationSec"
        }
        require(decelThreshold < 0.0) { "decelThreshold must be negative" }
        require(speedZones.size == SpeedZone.entries.size - 1) {
            "speedZones needs ${SpeedZone.entries.size - 1} upper bounds, got ${speedZones.size}"
        }
        require(speedZones.first() > 0.0 && speedZones.zipWithNext().all { (a, b) -> a < b }) {
            "speedZones must be positive and strictly increasing"
        }
        require(hrMax > 0) { "hrMax must be positive" }
    }
}
