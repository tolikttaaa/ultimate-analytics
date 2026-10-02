package com.ttaaa.ultimate.analysis.metrics

import com.ttaaa.ultimate.analysis.RawSessions.sample
import com.ttaaa.ultimate.analysis.pause.markPauses
import com.ttaaa.ultimate.domain.AnalysisParameters
import com.ttaaa.ultimate.domain.TimeMetrics
import com.ttaaa.ultimate.domain.TimeRange
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.pair
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class TimeAndSpeedTest {

    private val params = AnalysisParameters()

    // Samples at t = 0..99 without 40..44 (a gap), paused during [10, 30).
    private val samples = markPauses((0..99).filterNot { it in 40..44 }.map { sample(it, 3.0) }, listOf(TimeRange(10, 30)))

    @Test
    fun `active time is elapsed time minus pauses and gaps`() {
        timeMetrics(samples, TimeRange(0, 100)) shouldBe
            TimeMetrics(elapsedSec = 100, activeSec = 75, pausedSec = 20, gapSec = 5, workRestRatio = 3.75)
        timeMetrics(samples, TimeRange(20, 50)) shouldBe
            TimeMetrics(elapsedSec = 30, activeSec = 15, pausedSec = 10, gapSec = 5, workRestRatio = 1.5)
    }

    @Test
    fun `a window covers the seconds from its start up to its end`() {
        timeMetrics(samples, TimeRange(9, 10)).pausedSec shouldBe 0
        timeMetrics(samples, TimeRange(29, 30)).pausedSec shouldBe 1
        timeMetrics(samples, TimeRange(30, 30)) shouldBe TimeMetrics(0, 0, 0, 0, workRestRatio = null)
    }

    @Test
    fun `seconds beyond the samples are gap`() {
        timeMetrics(samples, TimeRange(95, 110)) shouldBe TimeMetrics(15, 5, 0, 10, workRestRatio = null)
    }

    @Test
    fun `moving speed averages moving samples that are recorded and not paused`() {
        val samples = listOf(
            sample(0, 4.0),
            sample(1, 6.0),
            sample(2, 1.9), // walking
            sample(3, 8.0, interpolated = true),
            sample(4, 9.0, inPause = true),
            sample(5, 2.0), // exactly walkSpeedThreshold counts as moving
        )

        movingSpeed(samples, TimeRange(0, 6), params) shouldBe 4.0
        movingSpeed(samples, TimeRange(2, 5), params) shouldBe null
    }

    @Test
    fun `active, paused and gap seconds add up to elapsed, and adjacent windows add up`() = runTest {
        // Samples present per second, with random pauses, then random cut points a <= b <= c.
        val cases = Arb.pair(Arb.list(Arb.int(0..3), 1..200), Arb.list(Arb.int(0..250), 3..3))

        checkAll(cases) { (kinds, cuts) ->
            // kind 0: gap, 1: active, 2-3: paused
            val present = kinds.withIndex().filter { it.value != 0 }
            val samples = present.map { (t, kind) -> sample(t, 3.0, inPause = kind >= 2) }
            val (a, b, c) = cuts.sorted()

            listOf(TimeRange(a, b), TimeRange(b, c), TimeRange(a, c)).forEach { range ->
                val metrics = timeMetrics(samples, range)
                metrics.activeSec + metrics.pausedSec + metrics.gapSec shouldBe metrics.elapsedSec
                metrics.activeSec shouldBeGreaterThanOrEqual 0
            }
            val first = timeMetrics(samples, TimeRange(a, b))
            val second = timeMetrics(samples, TimeRange(b, c))
            val whole = timeMetrics(samples, TimeRange(a, c))
            first.activeSec + second.activeSec shouldBe whole.activeSec
            first.pausedSec + second.pausedSec shouldBe whole.pausedSec
            first.gapSec + second.gapSec shouldBe whole.gapSec
        }
    }
}
