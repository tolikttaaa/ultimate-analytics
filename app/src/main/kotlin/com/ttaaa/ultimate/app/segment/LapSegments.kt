package com.ttaaa.ultimate.app.segment

import com.ttaaa.ultimate.domain.Effort
import com.ttaaa.ultimate.domain.Lap
import com.ttaaa.ultimate.domain.Segment
import com.ttaaa.ultimate.domain.SegmentSource
import java.util.UUID

/**
 * The initial segments of a session (spec 6.1 step 10): one per watch lap, labelled "Lap n". Laps shorter than
 * [Segment.MIN_DURATION_SEC] or overlapping an earlier lap are skipped, so the segments keep their invariants.
 */
fun segmentsFromLaps(sessionId: UUID, laps: List<Lap>, newId: () -> UUID = UUID::randomUUID): List<Segment> {
    val segments = mutableListOf<Segment>()
    for (lap in laps.sortedBy { it.range.fromT }) {
        if (lap.range.durationSec < Segment.MIN_DURATION_SEC) continue
        if (segments.any { it.range.overlaps(lap.range) }) continue
        segments += Segment(newId(), sessionId, lap.range, drillTypeId = null, label = "Lap ${lap.index + 1}", SegmentSource.LAP)
    }
    return segments
}

/** The segment whose seconds contain the start of [effort] (spec 5, invariant 3), or null. */
fun segmentOf(effort: Effort, segments: List<Segment>): Segment? =
    segments.firstOrNull { effort.startT >= it.range.fromT && effort.startT < it.range.toT }
