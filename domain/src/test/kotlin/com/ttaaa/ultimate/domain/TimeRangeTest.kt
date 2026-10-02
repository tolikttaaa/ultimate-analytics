package com.ttaaa.ultimate.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class TimeRangeTest {

    private val ranges = Arb.bind(Arb.int(0..300), Arb.int(0..120)) { from, length -> TimeRange(from, from + length) }

    @Test
    fun `duration is the time between the bounds`() {
        TimeRange(600, 1200).durationSec shouldBe 600
        TimeRange(5, 5).durationSec shouldBe 0
    }

    @Test
    fun `rejects a negative start and reversed bounds`() {
        shouldThrow<IllegalArgumentException> { TimeRange(-1, 10) }
        shouldThrow<IllegalArgumentException> { TimeRange(10, 9) }
    }

    @Test
    fun `ranges that only touch do not overlap`() {
        TimeRange(0, 60).overlaps(TimeRange(60, 120)) shouldBe false
        TimeRange(0, 61).overlaps(TimeRange(60, 120)) shouldBe true
        TimeRange(10, 20).overlaps(TimeRange(0, 100)) shouldBe true
    }

    @Test
    fun `ranges overlap exactly when they share a second`() = runTest {
        checkAll(ranges, ranges) { a, b ->
            val sharedSeconds = (a.fromT..<a.toT).toSet() intersect (b.fromT..<b.toT).toSet()

            a.overlaps(b) shouldBe sharedSeconds.isNotEmpty()
            b.overlaps(a) shouldBe a.overlaps(b)
        }
    }
}
