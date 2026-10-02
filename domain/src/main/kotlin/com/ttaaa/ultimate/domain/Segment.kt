package com.ttaaa.ultimate.domain

import java.util.UUID

enum class SegmentSource { LAP, MANUAL }

/**
 * A named, typed time interval inside a session, e.g. a drill, a game or a rest (spec 3, 5).
 * Segments of one session never overlap ([TimeRange.overlaps]) and lie inside `[0, lastT]` of their session;
 * the service that owns the session checks both (spec 5, invariants 1-2).
 */
data class Segment(
    val id: UUID,
    val sessionId: UUID,
    val range: TimeRange,
    val drillTypeId: UUID?,
    val label: String?,
    val source: SegmentSource,
) {
    init {
        require(range.durationSec >= MIN_DURATION_SEC) {
            "A segment lasts at least $MIN_DURATION_SEC s: $range"
        }
    }

    companion object {
        /** Spec 5, invariant 1. */
        const val MIN_DURATION_SEC = 10
    }
}
