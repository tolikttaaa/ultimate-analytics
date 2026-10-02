package com.ttaaa.ultimate.domain

/** A lap recorded by the watch (Lap button or auto-lap); read-only copy of the FIT data (spec 3, 5). */
data class Lap(
    /** Position of the lap in the session, from 0. */
    val index: Int,
    val range: TimeRange,
    /** FIT `lap_trigger`, e.g. `manual` or `time`. */
    val trigger: String?,
) {
    init {
        require(index >= 0) { "Negative lap index: $index" }
    }
}
