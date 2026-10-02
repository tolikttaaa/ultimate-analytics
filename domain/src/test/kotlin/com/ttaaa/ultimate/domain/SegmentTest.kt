package com.ttaaa.ultimate.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.UUID

class SegmentTest {

    private fun segment(range: TimeRange) =
        Segment(UUID.randomUUID(), UUID.randomUUID(), range, drillTypeId = null, label = null, SegmentSource.MANUAL)

    @Test
    fun `lasts at least 10 seconds`() {
        segment(TimeRange(100, 110)).range.durationSec shouldBe 10
        shouldThrow<IllegalArgumentException> { segment(TimeRange(100, 109)) }
    }
}
