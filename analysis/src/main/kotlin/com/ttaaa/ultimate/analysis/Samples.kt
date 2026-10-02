package com.ttaaa.ultimate.analysis

import com.ttaaa.ultimate.domain.Sample
import com.ttaaa.ultimate.domain.TimeRange

/*
 * Time accounting: a sample at `t` stands for the second [t, t+1). A range [fromT, toT] covers the seconds
 * fromT..toT-1, so its duration is `toT - fromT` and adjacent ranges never share a second (docs/DECISIONS.md).
 */

/** Maximal runs of consecutive seconds in [items] sorted by [t], as index ranges into [items]. */
internal fun <T> consecutiveRuns(items: List<T>, t: (T) -> Int): List<IntRange> {
    val runs = mutableListOf<IntRange>()
    var runStart = 0
    for (i in 1..items.size) {
        if (i == items.size || t(items[i]) != t(items[i - 1]) + 1) {
            if (i > runStart) runs += runStart..<i
            runStart = i
        }
    }
    return runs
}

/** Maximal runs of samples without a gap, as index ranges. Nothing may span two runs (spec 6.1). */
fun List<Sample>.runs(): List<IntRange> = consecutiveRuns(this) { it.t }

/** The samples of the seconds covered by [range] (`fromT <= t < toT`) in samples sorted by `t`. */
fun List<Sample>.inRange(range: TimeRange): List<Sample> = indicesIn(range).let { subList(it.first, it.last + 1) }

/** Indices of the samples of the seconds covered by [range] in samples sorted by `t`. */
fun List<Sample>.indicesIn(range: TimeRange): IntRange = lowerBound(range.fromT)..<lowerBound(range.toT)

private fun List<Sample>.lowerBound(t: Int): Int {
    val index = binarySearchBy(t) { it.t }
    return if (index >= 0) index else -index - 1
}
