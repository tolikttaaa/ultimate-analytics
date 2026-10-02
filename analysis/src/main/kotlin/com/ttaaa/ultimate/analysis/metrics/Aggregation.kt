package com.ttaaa.ultimate.analysis.metrics

import com.ttaaa.ultimate.domain.AnalysisParameters
import com.ttaaa.ultimate.domain.DistanceMetrics
import com.ttaaa.ultimate.domain.EffortStats
import com.ttaaa.ultimate.domain.FatigueMetrics
import com.ttaaa.ultimate.domain.HeartRateMetrics
import com.ttaaa.ultimate.domain.MeanAndBest
import com.ttaaa.ultimate.domain.SpeedZone
import com.ttaaa.ultimate.domain.SpeedZoneMetrics
import com.ttaaa.ultimate.domain.TimeMetrics
import com.ttaaa.ultimate.domain.WindowMetrics

/**
 * Totals of several windows, e.g. all segments of one drill type across sessions (spec 6.5). Counts, durations,
 * distances and zones are summed. Means are weighted: effort metrics and fatigue by effort count, moving speed by
 * active seconds, heart rate by the recorded seconds (`elapsedSec − gapSec`). Bests are the best of the windows.
 * The result has no range.
 */
fun aggregateMetrics(windows: List<WindowMetrics>): WindowMetrics {
    val activeSec = windows.sumOf { it.time.activeSec }
    val pausedSec = windows.sumOf { it.time.pausedSec }
    val effortCount = windows.sumOf { it.efforts.count }
    val movingSpeed = weightedMean(windows) { it.distance.movingSpeed to it.time.activeSec.toDouble() }

    return WindowMetrics(
        range = null,
        time = TimeMetrics(
            elapsedSec = windows.sumOf { it.time.elapsedSec },
            activeSec = activeSec,
            pausedSec = pausedSec,
            gapSec = windows.sumOf { it.time.gapSec },
            workRestRatio = if (pausedSec > 0) activeSec.toDouble() / pausedSec else null,
        ),
        distance = DistanceMetrics(
            distanceM = windows.sumOf { it.distance.distanceM },
            activeDistanceM = windows.sumOf { it.distance.activeDistanceM },
            movingSpeed = movingSpeed,
            movingPaceSecPerKm = movingSpeed?.let(::paceSecPerKm),
            maxSpeed = windows.mapNotNull { it.distance.maxSpeed }.maxOrNull(),
        ),
        zones = SpeedZone.entries.map { zone ->
            val inZone = windows.mapNotNull { window -> window.zones.find { it.zone == zone } }
            SpeedZoneMetrics(zone, timeSec = inZone.sumOf { it.timeSec }, distanceM = inZone.sumOf { it.distanceM })
        },
        efforts = EffortStats(
            count = effortCount,
            perActiveMin = if (activeSec > 0) effortCount * 60.0 / activeSec else null,
            peakSpeed = aggregateStat(windows) { it.peakSpeed },
            meanSpeed = aggregateStat(windows) { it.meanSpeed },
            meanAccel = aggregateStat(windows) { it.meanAccel },
            peakAccel = aggregateStat(windows) { it.peakAccel },
            meanSpeedFirst3s = aggregateStat(windows) { it.meanSpeedFirst3s },
            timeTo80PctPeakSec = aggregateStat(windows, lowerIsBetter = true) { it.timeTo80PctPeakSec },
        ),
        decelCount = windows.sumOf { it.decelCount },
        fatigue = FatigueMetrics(
            peakSpeedDropPct = weightedMean(windows) { it.fatigue.peakSpeedDropPct to it.efforts.count.toDouble() },
        ),
        heartRate = aggregateHeartRate(windows),
    )
}

private fun aggregateStat(
    windows: List<WindowMetrics>,
    lowerIsBetter: Boolean = false,
    stat: (EffortStats) -> MeanAndBest?,
): MeanAndBest? {
    val mean = weightedMean(windows) { stat(it.efforts)?.mean to it.efforts.count.toDouble() } ?: return null
    val bests = windows.mapNotNull { stat(it.efforts)?.best }
    return MeanAndBest(mean, if (lowerIsBetter) bests.min() else bests.max())
}

private fun aggregateHeartRate(windows: List<WindowMetrics>): HeartRateMetrics {
    fun recordedSec(window: WindowMetrics) = (window.time.elapsedSec - window.time.gapSec).toDouble()
    val withZones = windows.filter { it.heartRate.zonesPct.isNotEmpty() }
    return HeartRateMetrics(
        avg = weightedMean(windows) { it.heartRate.avg to recordedSec(it) },
        max = windows.mapNotNull { it.heartRate.max }.maxOrNull(),
        zonesPct = if (withZones.isEmpty()) {
            emptyList()
        } else {
            (0..<AnalysisParameters.HR_ZONES).map { zone ->
                checkNotNull(weightedMean(withZones) { it.heartRate.zonesPct[zone] to recordedSec(it) })
            }
        },
    )
}

/** Mean of the non-null values weighted by their positive weights; null if there is none. */
private fun weightedMean(windows: List<WindowMetrics>, valueAndWeight: (WindowMetrics) -> Pair<Double?, Double>): Double? {
    val weighted = windows.map(valueAndWeight).filter { (value, weight) -> value != null && weight > 0.0 }
    val totalWeight = weighted.sumOf { it.second }
    return if (totalWeight == 0.0) null else weighted.sumOf { (value, weight) -> value!! * weight } / totalWeight
}
