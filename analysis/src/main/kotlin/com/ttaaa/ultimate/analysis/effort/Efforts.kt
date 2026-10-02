package com.ttaaa.ultimate.analysis.effort

import com.ttaaa.ultimate.analysis.runs
import com.ttaaa.ultimate.domain.AnalysisParameters
import com.ttaaa.ultimate.domain.Effort
import com.ttaaa.ultimate.domain.EffortMetrics
import com.ttaaa.ultimate.domain.Sample

/** `timeTo80PctPeakSec` measures the time to this share of the peak speed (spec 6.4). */
private const val PEAK_SHARE = 0.8

/** `speedAtNs`, `meanSpeedFirst3s` and `distanceFirst3s` look at the first seconds of an effort (spec 6.4). */
private const val FIRST_SECONDS = 3

/** `maxDecelAfter` looks this far past the end of the effort (spec 6.4). */
private const val DECEL_AFTER_END_SEC = 3

/** `hrMax` looks this far past the end of the effort, as heart rate lags behind the sprint (spec 6.4). */
private const val HR_AFTER_END_SEC = 10

/**
 * Detects efforts (sprints, cuts) in smoothed samples (spec 6.4). Efforts never overlap and never span a gap.
 *
 * 1. **Trigger:** acceleration reaches `effortStartAccel` right after a sample below it.
 * 2. **Start:** the lowest speed among the trigger and the `effortStartLookbackSec` samples before it (the latest one on
 *    ties), but not before the end of the previous effort.
 * 3. **Peak and end:** walking forward, the peak is the highest speed so far; the effort ends at the first later sample
 *    below `max(peakSpeed × effortEndPeakRatio, effortEndMinSpeed)`, at the latest `effortMaxDurationSec` after the
 *    start. An effort that reaches a gap or the end of the data before it ends is rejected.
 * 4. **Accept** if the peak speed, the speed gain and the duration reach their minimums. The next trigger is searched
 *    after the end of an accepted effort, or after the trigger of a rejected one.
 */
fun detectEfforts(samples: List<Sample>, params: AnalysisParameters): List<Effort> {
    val efforts = mutableListOf<Effort>()
    for (run in samples.runs()) {
        var earliestStart = run.first
        var i = run.first + 1
        while (i <= run.last) {
            val trigger = samples[i].accel >= params.effortStartAccel && samples[i - 1].accel < params.effortStartAccel
            val effort = if (trigger) candidate(samples, run, i, earliestStart, params) else null
            if (effort == null) {
                i++
            } else {
                efforts += effort
                val endIndex = i + (effort.endT - samples[i].t)
                earliestStart = endIndex
                i = endIndex + 1
            }
        }
    }
    return efforts
}

private fun candidate(
    samples: List<Sample>,
    run: IntRange,
    trigger: Int,
    earliestStart: Int,
    params: AnalysisParameters,
): Effort? {
    fun speed(index: Int) = samples[index].speed

    val start = (maxOf(trigger - params.effortStartLookbackSec, earliestStart)..trigger)
        .minWith(compareBy<Int> { speed(it) }.thenByDescending { it })
    val cap = start + params.effortMaxDurationSec
    var peak = start
    var end = -1
    for (k in start + 1..minOf(cap, run.last)) {
        if (speed(k) > speed(peak)) {
            peak = k
        } else if (speed(k) < maxOf(speed(peak) * params.effortEndPeakRatio, params.effortEndMinSpeed)) {
            end = k
            break
        }
    }
    if (end < 0) {
        if (cap > run.last) return null // the effort runs into a gap or the end of the data
        end = cap
    }

    val accepted = speed(peak) >= params.effortMinPeakSpeed &&
        speed(peak) - speed(start) >= params.effortMinSpeedGain &&
        end - start >= params.effortMinDurationSec
    if (!accepted) return null
    return Effort(samples[start].t, samples[peak].t, samples[end].t, metrics(samples, run, start, peak, end))
}

/** Per-effort metrics of spec 6.4; indices are positions in [samples], consecutive seconds within [run]. */
private fun metrics(samples: List<Sample>, run: IntRange, start: Int, peak: Int, end: Int): EffortMetrics {
    fun speed(index: Int) = samples[index].speed
    fun speedAt(seconds: Int) = if (start + seconds <= end) speed(start + seconds) else null

    val firstSeconds = start + 1..start + FIRST_SECONDS
    val target = PEAK_SHARE * speed(peak)
    val reached = (start..peak).first { speed(it) >= target }
    val timeToTarget = if (reached == start) {
        0.0
    } else {
        (reached - 1 - start) + (target - speed(reached - 1)) / (speed(reached) - speed(reached - 1))
    }

    return EffortMetrics(
        startSpeed = speed(start),
        peakSpeed = speed(peak),
        timeToPeakSec = peak - start,
        durationSec = end - start,
        distanceM = (start..<end).sumOf(::speed),
        meanSpeed = (start..end).sumOf(::speed) / (end - start + 1),
        meanAccel = (speed(peak) - speed(start)) / (peak - start),
        peakAccel = (start..peak).maxOf { samples[it].accel },
        speedAt1s = speedAt(1),
        speedAt2s = speedAt(2),
        speedAt3s = speedAt(3),
        meanSpeedFirst3s = if (firstSeconds.last <= run.last) firstSeconds.sumOf(::speed) / FIRST_SECONDS else null,
        distanceFirst3s = (start..minOf(start + FIRST_SECONDS - 1, run.last)).sumOf(::speed),
        timeTo80PctPeakSec = timeToTarget,
        maxDecelAfter = (peak..minOf(end + DECEL_AFTER_END_SEC, run.last)).minOf { samples[it].accel },
        hrStart = samples[start].hr,
        hrMax = (start..minOf(end + HR_AFTER_END_SEC, run.last)).mapNotNull { samples[it].hr }.maxOrNull(),
    )
}
