package com.ttaaa.ultimate.domain

/**
 * A time interval inside a session in integer seconds from the first sample (`t = 0`), which keeps
 * 1 Hz data index-addressable (spec 5). [durationSec] is the time between the two bounds.
 */
data class TimeRange(val fromT: Int, val toT: Int) {
    init {
        require(fromT in 0..toT) { "Invalid time range [$fromT, $toT]" }
    }

    val durationSec: Int get() = toT - fromT

    /**
     * True when the ranges share some time. Ranges that only touch, such as `[0, 60]` and `[60, 120]`,
     * do not overlap, so the two parts of a split segment are valid neighbours.
     */
    fun overlaps(other: TimeRange): Boolean = maxOf(fromT, other.fromT) < minOf(toT, other.toT)
}
