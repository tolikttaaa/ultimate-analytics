package com.ttaaa.ultimate.analysis.pause

import com.ttaaa.ultimate.analysis.RawSessions.sample
import com.ttaaa.ultimate.analysis.RawSessions.samples
import com.ttaaa.ultimate.domain.AnalysisParameters
import com.ttaaa.ultimate.domain.Sample
import com.ttaaa.ultimate.domain.TimeRange
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.triple
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class PausesTest {

    private val params = AnalysisParameters() // below 1.5 m/s, at least 20 s, spikes up to 2 s
    private val running = 4.0
    private val standing = 0.5

    @Test
    fun `a slow period of at least minPauseDurationSec is a pause`() {
        detectPauses(samples(0, 30 to running, 25 to standing, 30 to running), params) shouldBe listOf(TimeRange(30, 55))
    }

    @Test
    fun `shorter slow periods are not pauses`() {
        detectPauses(samples(0, 30 to running, 19 to standing, 30 to running), params) shouldBe emptyList()
        detectPauses(samples(0, 30 to running, 20 to standing, 30 to running), params) shouldBe listOf(TimeRange(30, 50))
    }

    @Test
    fun `the threshold speed itself is not slow`() {
        detectPauses(samples(0, 30 to params.pauseSpeedThreshold), params) shouldBe emptyList()
    }

    @Test
    fun `short spikes inside a pause belong to it`() {
        val samples = samples(0, 30 to running, 15 to standing, 2 to 3.0, 10 to standing, 30 to running)

        detectPauses(samples, params) shouldBe listOf(TimeRange(30, 57))
    }

    @Test
    fun `longer spikes split a pause`() {
        detectPauses(samples(0, 15 to standing, 3 to 3.0, 10 to standing), params) shouldBe emptyList()
        detectPauses(samples(0, 25 to standing, 3 to 3.0, 25 to standing), params) shouldBe
            listOf(TimeRange(0, 25), TimeRange(28, 53))
    }

    @Test
    fun `a gap ends a pause`() {
        detectPauses(samples(0, 15 to standing) + samples(25, 15 to standing), params) shouldBe emptyList()
        detectPauses(samples(0, 25 to standing) + samples(30, 25 to standing), params) shouldBe
            listOf(TimeRange(0, 25), TimeRange(30, 55))
    }

    @Test
    fun `marks exactly the paused seconds`() {
        val marked = markPauses(samples(0, 10 to running), listOf(TimeRange(2, 4), TimeRange(7, 8)))

        marked.filter { it.inPause }.map { it.t } shouldBe listOf(2, 3, 7)
    }

    @Test
    fun `pauses are long enough, gap-free, bounded by slow samples and cover every long slow stretch`() = runTest {
        // Parts of (seconds, speed, missing seconds before the part).
        val parts = Arb.list(Arb.triple(Arb.int(1..30), Arb.element(0.0, 1.0, 1.49, 1.5, 3.0, 6.0), Arb.int(0..3)), 1..20)

        checkAll(parts) { spec ->
            var t = 0
            val samples = spec.flatMap { (seconds, speed, missing) ->
                t += missing
                List(seconds) { sample(t++, speed) }
            }
            val pauses = detectPauses(samples, params)
            val byT = samples.associateBy { it.t }
            fun slow(t: Int) = byT.getValue(t).speed < params.pauseSpeedThreshold

            pauses.zipWithNext().forEach { (a, b) -> a.toT shouldBeLessThanOrEqual b.fromT }
            pauses.forEach { pause ->
                pause.durationSec shouldBeGreaterThanOrEqual params.minPauseDurationSec
                (pause.fromT..<pause.toT).all { it in byT } shouldBe true
                slow(pause.fromT) shouldBe true
                slow(pause.toT - 1) shouldBe true
                longestRun((pause.fromT..<pause.toT).map { !slow(it) }) shouldBeLessThanOrEqual params.pauseSpikeToleranceSec
            }
            slowStretches(samples).filter { it.count() >= params.minPauseDurationSec }.forEach { stretch ->
                pauses.any { it.fromT <= stretch.first && stretch.last < it.toT } shouldBe true
            }
        }
    }

    private fun longestRun(flags: List<Boolean>): Int {
        var longest = 0
        var current = 0
        for (flag in flags) {
            current = if (flag) current + 1 else 0
            longest = maxOf(longest, current)
        }
        return longest
    }

    /** Maximal runs of consecutive slow seconds, without any tolerance; a gap breaks a run. */
    private fun slowStretches(samples: List<Sample>): List<IntRange> =
        samples.filter { it.speed < params.pauseSpeedThreshold }.map { it.t }.fold(mutableListOf()) { stretches, t ->
            if (stretches.lastOrNull()?.last == t - 1) stretches[stretches.lastIndex] = stretches.last().first..t
            else stretches += t..t
            stretches
        }
}
