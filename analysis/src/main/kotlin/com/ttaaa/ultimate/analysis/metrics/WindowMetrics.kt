package com.ttaaa.ultimate.analysis.metrics

import com.ttaaa.ultimate.analysis.inRange
import com.ttaaa.ultimate.analysis.indicesIn
import com.ttaaa.ultimate.domain.AnalysisParameters
import com.ttaaa.ultimate.domain.DistanceMetrics
import com.ttaaa.ultimate.domain.Effort
import com.ttaaa.ultimate.domain.EffortStats
import com.ttaaa.ultimate.domain.FatigueMetrics
import com.ttaaa.ultimate.domain.HeartRateMetrics
import com.ttaaa.ultimate.domain.MeanAndBest
import com.ttaaa.ultimate.domain.Sample
import com.ttaaa.ultimate.domain.SpeedZone
import com.ttaaa.ultimate.domain.SpeedZoneMetrics
import com.ttaaa.ultimate.domain.TimeRange
import com.ttaaa.ultimate.domain.WindowMetrics

/** `peakSpeedDropPct` needs at least this many efforts (spec 6.5). */
private const val MIN_EFFORTS_FOR_FATIGUE = 6

/**
 * Metrics of the window [range] of one analysed session (spec 6.5): the same function serves the whole session,
 * a segment and a brushed window. [samples] are sorted by `t` and marked with their pauses; an effort belongs to the
 * window when its `startT` is one of the window's seconds `fromT..toT-1`.
 *
 * Distances are sums of the smoothed speed over the window's seconds (1 s each), as for efforts in spec 6.4, except that
 * seconds below `minDistanceSpeed` cover no distance: standing still is not movement, whatever the speed noise says.
 */
fun windowMetrics(
    samples: List<Sample>,
    efforts: List<Effort>,
    range: TimeRange,
    params: AnalysisParameters,
): WindowMetrics {
    val window = samples.inRange(range)
    fun distance(of: List<Sample>) = of.sumOf { distanceM(it, params) }
    val time = timeMetrics(samples, range)
    val moving = movingSpeed(samples, range, params)
    val windowEfforts = efforts.filter { it.startT >= range.fromT && it.startT < range.toT }.sortedBy { it.startT }
    return WindowMetrics(
        range = range,
        time = time,
        distance = DistanceMetrics(
            distanceM = distance(window),
            activeDistanceM = distance(window.filterNot { it.inPause }),
            movingSpeed = moving,
            movingPaceSecPerKm = moving?.let(::paceSecPerKm),
            maxSpeed = window.maxOfOrNull { it.speed },
        ),
        zones = speedZones(window, params),
        efforts = effortStats(windowEfforts, time.activeSec),
        decelCount = decelCount(samples, range, params),
        fatigue = FatigueMetrics(peakSpeedDropPct(windowEfforts)),
        heartRate = heartRate(window, params),
    )
}

/** Distance covered in the second of [sample], m: its speed, or 0 below `minDistanceSpeed`. */
fun distanceM(sample: Sample, params: AnalysisParameters): Double =
    if (sample.speed >= params.minDistanceSpeed) sample.speed else 0.0

/** Pace at [speed] (m/s), s/km. */
fun paceSecPerKm(speed: Double): Double = 1000.0 / speed

/** Zone of [speed]: the first zone whose upper bound (`speedZones`) is above it, SPRINT above the last bound. */
fun speedZone(speed: Double, params: AnalysisParameters): SpeedZone {
    val index = params.speedZones.indexOfFirst { speed < it }
    return if (index < 0) SpeedZone.SPRINT else SpeedZone.entries[index]
}

private fun speedZones(window: List<Sample>, params: AnalysisParameters): List<SpeedZoneMetrics> {
    val byZone = window.groupBy { speedZone(it.speed, params) }
    return SpeedZone.entries.map { zone ->
        val samples = byZone[zone].orEmpty()
        SpeedZoneMetrics(zone, timeSec = samples.size, distanceM = samples.sumOf { distanceM(it, params) })
    }
}

private fun effortStats(efforts: List<Effort>, activeSec: Int): EffortStats {
    val metrics = efforts.map { it.metrics }
    fun stat(values: List<Double>, lowerIsBetter: Boolean = false): MeanAndBest? =
        if (values.isEmpty()) null else MeanAndBest(values.average(), if (lowerIsBetter) values.min() else values.max())
    return EffortStats(
        count = efforts.size,
        perActiveMin = if (activeSec > 0) efforts.size * 60.0 / activeSec else null,
        peakSpeed = stat(metrics.map { it.peakSpeed }),
        meanSpeed = stat(metrics.map { it.meanSpeed }),
        meanAccel = stat(metrics.map { it.meanAccel }),
        peakAccel = stat(metrics.map { it.peakAccel }),
        meanSpeedFirst3s = stat(metrics.mapNotNull { it.meanSpeedFirst3s }),
        timeTo80PctPeakSec = stat(metrics.map { it.timeTo80PctPeakSec }, lowerIsBetter = true),
    )
}

/**
 * Runs of `accel ≤ decelThreshold` (any run lasts at least 1 s at 1 Hz), counted in the window where they start so that
 * the counts of adjacent windows add up; a gap ends a run.
 */
private fun decelCount(samples: List<Sample>, range: TimeRange, params: AnalysisParameters): Int =
    samples.indicesIn(range).count { i ->
        val decelerating = samples[i].accel <= params.decelThreshold
        val previous = samples.getOrNull(i - 1)?.takeIf { it.t == samples[i].t - 1 }
        decelerating && (previous == null || previous.accel > params.decelThreshold)
    }

/** `(mean peakSpeed of the first third − mean of the last third) / first-third mean × 100`; thirds are ⌊n/3⌋ efforts. */
private fun peakSpeedDropPct(efforts: List<Effort>): Double? {
    if (efforts.size < MIN_EFFORTS_FOR_FATIGUE) return null
    val third = efforts.size / 3
    val first = efforts.take(third).map { it.metrics.peakSpeed }.average()
    val last = efforts.takeLast(third).map { it.metrics.peakSpeed }.average()
    return (first - last) / first * 100
}

private fun heartRate(window: List<Sample>, params: AnalysisParameters): HeartRateMetrics {
    val heartRates = window.mapNotNull { it.hr }
    if (heartRates.isEmpty()) return HeartRateMetrics(avg = null, max = null, zonesPct = emptyList())
    val lowerBounds = params.hrZoneLowerBoundsPct.map { it / 100.0 * params.hrMax }
    val secondsInZone = IntArray(lowerBounds.size)
    for (hr in heartRates) {
        val zone = lowerBounds.indexOfLast { hr >= it }
        if (zone >= 0) secondsInZone[zone]++
    }
    return HeartRateMetrics(
        avg = heartRates.average(),
        max = heartRates.max(),
        zonesPct = secondsInZone.map { it * 100.0 / heartRates.size },
    )
}
