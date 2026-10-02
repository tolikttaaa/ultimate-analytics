package com.ttaaa.ultimate.analysis.pause

import com.ttaaa.ultimate.analysis.runs
import com.ttaaa.ultimate.domain.AnalysisParameters
import com.ttaaa.ultimate.domain.Sample
import com.ttaaa.ultimate.domain.TimeRange

/**
 * Detects pauses (spec 6.3): maximal runs of samples with smoothed speed below `pauseSpeedThreshold` lasting at least
 * `minPauseDurationSec`. Excursions above the threshold of at most `pauseSpikeToleranceSec` between slow samples do
 * not break a run and belong to the pause. A pause never spans a gap.
 *
 * A pause `[fromT, toT)` covers the samples `fromT..toT-1`, so its duration is the number of paused seconds.
 */
fun detectPauses(samples: List<Sample>, params: AnalysisParameters): List<TimeRange> {
    val pauses = mutableListOf<TimeRange>()
    fun close(first: Int, last: Int) {
        if (last - first + 1 >= params.minPauseDurationSec) pauses += TimeRange(samples[first].t, samples[last].t + 1)
    }

    for (run in samples.runs()) {
        var first = -1
        var lastSlow = -1
        for (i in run) {
            if (samples[i].speed >= params.pauseSpeedThreshold) continue
            if (first >= 0 && i - lastSlow - 1 > params.pauseSpikeToleranceSec) {
                close(first, lastSlow)
                first = -1
            }
            if (first < 0) first = i
            lastSlow = i
        }
        if (first >= 0) close(first, lastSlow)
    }
    return pauses
}

/** The [samples] with `inPause` set exactly for the seconds of [pauses] (sorted, not overlapping). */
fun markPauses(samples: List<Sample>, pauses: List<TimeRange>): List<Sample> {
    var next = 0
    return samples.map { sample ->
        while (next < pauses.size && pauses[next].toT <= sample.t) next++
        val inPause = next < pauses.size && sample.t >= pauses[next].fromT
        if (sample.inPause == inPause) sample else sample.copy(inPause = inPause)
    }
}
