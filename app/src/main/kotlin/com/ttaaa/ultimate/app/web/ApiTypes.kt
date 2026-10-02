package com.ttaaa.ultimate.app.web

import com.ttaaa.ultimate.domain.Lap
import com.ttaaa.ultimate.domain.Segment
import com.ttaaa.ultimate.domain.SegmentSource
import java.util.UUID

/** One page of a list. */
data class Page<T>(val items: List<T>, val page: Int, val size: Int, val totalItems: Long, val totalPages: Int)

data class LapDto(val index: Int, val startT: Int, val endT: Int, val trigger: String?) {
    constructor(lap: Lap) : this(lap.index, lap.range.fromT, lap.range.toT, lap.trigger)
}

data class SegmentDto(
    val id: UUID,
    val startT: Int,
    val endT: Int,
    val drillTypeId: UUID?,
    val label: String?,
    val source: SegmentSource,
) {
    constructor(segment: Segment) :
        this(segment.id, segment.range.fromT, segment.range.toT, segment.drillTypeId, segment.label, segment.source)
}
