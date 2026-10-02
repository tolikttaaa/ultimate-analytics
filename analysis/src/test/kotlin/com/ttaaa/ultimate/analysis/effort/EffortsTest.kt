package com.ttaaa.ultimate.analysis.effort

import com.ttaaa.ultimate.analysis.RawSessions.T0
import com.ttaaa.ultimate.analysis.RawSessions.rawSession
import com.ttaaa.ultimate.analysis.RawSessions.record
import com.ttaaa.ultimate.analysis.grid.Grid
import com.ttaaa.ultimate.analysis.grid.GridPoint
import com.ttaaa.ultimate.analysis.grid.buildGrid
import com.ttaaa.ultimate.analysis.smoothing.smooth
import com.ttaaa.ultimate.domain.AnalysisParameters
import com.ttaaa.ultimate.domain.Effort
import com.ttaaa.ultimate.domain.RecordingMode
import com.ttaaa.ultimate.domain.Sample
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.numericDouble
import io.kotest.property.arbitrary.triple
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class EffortsTest {

    private val params = AnalysisParameters()

    /** Samples from `t = 0`; without explicit [accels] the acceleration is the central difference of the speeds. */
    private fun series(speeds: List<Double>, accels: List<Double>? = null, hr: (Int) -> Int? = { null }) =
        speeds.indices.map { t ->
            val accel = accels?.get(t) ?: when (t) {
                0 -> speeds[1] - speeds[0]
                speeds.lastIndex -> speeds[t] - speeds[t - 1]
                else -> (speeds[t + 1] - speeds[t - 1]) / 2
            }
            Sample(t, T0.plusSeconds(t.toLong()), null, speeds[t], speeds[t], accel, hr(t), null, null, false, false)
        }

    private val sprint = listOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.5, 2.0, 4.0, 5.5, 6.5, 7.0, 7.0, 6.0, 4.5, 3.0, 2.0, 1.0) +
        List(9) { 0.5 }

    private fun List<Effort>.times() = map { Triple(it.startT, it.peakT, it.endT) }

    @Test
    fun `detects a sprint from standstill and measures it`() {
        // Trigger at t = 5 (accel 0.25 -> 1.0); start at the latest lowest speed of t = 3..5; peak 7.0 at t = 10;
        // end at t = 13, the first speed below 0.7 × 7.0 = 4.9.
        val effort = detectEfforts(series(sprint, hr = { 100 + it }), params).single()

        Triple(effort.startT, effort.peakT, effort.endT) shouldBe Triple(4, 10, 13)
        with(effort.metrics) {
            startSpeed shouldBe 0.0
            peakSpeed shouldBe 7.0
            timeToPeakSec shouldBe 6
            durationSec shouldBe 9
            distanceM shouldBe (38.5 plusOrMinus 1e-9) // speeds of t = 4..12
            meanSpeed shouldBe (4.3 plusOrMinus 1e-9) // speeds of t = 4..13
            meanAccel shouldBe (7.0 / 6 plusOrMinus 1e-9)
            peakAccel shouldBe (1.75 plusOrMinus 1e-9)
            speedAt1s shouldBe 0.5
            speedAt2s shouldBe 2.0
            speedAt3s shouldBe 4.0
            meanSpeedFirst3s!! shouldBe (6.5 / 3 plusOrMinus 1e-9)
            distanceFirst3s shouldBe (2.5 plusOrMinus 1e-9)
            timeTo80PctPeakSec shouldBe (4.1 plusOrMinus 1e-9) // 5.6 m/s between t = 8 (5.5) and t = 9 (6.5)
            maxDecelAfter shouldBe (-1.5 plusOrMinus 1e-9)
            hrStart shouldBe 104
            hrMax shouldBe 123 // t = 4..23
        }
    }

    @Test
    fun `rejects an acceleration that stays below effortMinPeakSpeed`() {
        val jog = listOf(1.0, 1.0, 1.0, 1.0, 2.0, 3.0, 4.0, 4.4, 4.4, 3.0, 2.0, 1.0, 1.0, 1.0)

        detectEfforts(series(jog), params) shouldBe emptyList()
    }

    @Test
    fun `rejects a speed gain below effortMinSpeedGain`() {
        val speeds = listOf(4.0, 4.0, 4.5, 5.0, 5.5, 5.5, 4.0, 3.0, 3.0)
        val accels = listOf(0.0, 0.0, 1.2, 1.2, 0.5, 0.0, -1.0, -1.0, 0.0)

        detectEfforts(series(speeds, accels), params) shouldBe emptyList()
    }

    @Test
    fun `cuts an effort at effortMaxDurationSec`() {
        val speeds = listOf(0.0, 0.0, 0.0, 2.0, 4.0, 6.0, 7.0) + List(30) { 7.5 }

        detectEfforts(series(speeds), params).times() shouldBe listOf(Triple(2, 7, 17))
    }

    @Test
    fun `rejects an effort that runs into a gap`() {
        val cut = series(sprint.take(12))
        val afterGap = series(List(10) { 0.5 }).map { it.copy(t = it.t + 20) }

        detectEfforts(cut + afterGap, params) shouldBe emptyList()
    }

    @Test
    fun `finds back-to-back efforts without overlap`() {
        // A capped effort until t = 17, then a new trigger at t = 18. The look-back would reach t = 16 (5.5 m/s),
        // but an effort cannot start before the previous one ended.
        val speeds = listOf(0.0, 0.0, 0.0, 3.0, 6.0) + List(11) { 7.0 } +
            listOf(5.5, 6.0, 7.0, 8.0, 9.0, 9.0, 5.0, 3.0) + List(7) { 3.0 }
        val accels = speeds.indices.map { if (it in 2..3) 1.5 else if (it == 18) 1.2 else 0.0 }

        detectEfforts(series(speeds, accels), params).times() shouldBe listOf(Triple(2, 5, 17), Triple(17, 20, 22))
    }

    @Test
    fun `efforts never overlap, never span a gap and meet the acceptance criteria`() = runTest {
        // Speed profile parts: (seconds to ramp linearly, target speed, missing seconds before the part).
        val parts = Arb.list(Arb.triple(Arb.int(1..12), Arb.numericDouble(0.0, 9.0), Arb.element(0, 0, 0, 0, 2, 5)), 2..40)

        var detected = 0
        checkAll(parts) { spec ->
            val points = mutableListOf<GridPoint>()
            var t = 0
            var speed = 0.0
            for ((seconds, target, missing) in spec) {
                t += missing
                repeat(seconds) { step ->
                    val value = speed + (target - speed) * (step + 1) / seconds
                    points += GridPoint(t, T0.plusSeconds(t.toLong()), null, value, value, null, null, null, false)
                    t++
                }
                speed = target
            }
            val samples = smooth(Grid(T0, points.last().t, RecordingMode.EVERY_SECOND, points), params)
            val byT = samples.associateBy { it.t }

            val efforts = detectEfforts(samples, params)
            detected += efforts.size

            efforts.zipWithNext().forEach { (a, b) -> a.endT shouldBeLessThanOrEqual b.startT }
            efforts.forEach { effort ->
                (effort.startT..effort.endT).all { it in byT } shouldBe true
                effort.metrics.peakSpeed shouldBe byT.getValue(effort.peakT).speed
                (effort.startT..effort.endT).maxOf { byT.getValue(it).speed } shouldBe effort.metrics.peakSpeed
                effort.metrics.peakSpeed shouldBeGreaterThanOrEqual params.effortMinPeakSpeed
                effort.metrics.peakSpeed - effort.metrics.startSpeed shouldBeGreaterThanOrEqual params.effortMinSpeedGain
                effort.metrics.durationSec shouldBeInRange params.effortMinDurationSec..params.effortMaxDurationSec
                effort.metrics.timeToPeakSec shouldBeGreaterThanOrEqual 1
            }
        }
        detected shouldBeGreaterThan 0 // the profiles do produce efforts
    }

    @Test
    fun `noise on a flat series never creates efforts`() = runTest {
        // The smoothing weights sum to at most 51/35 in absolute value, so noise within ±0.6 m/s moves smoothed
        // speeds by less than 0.9 m/s: no two of them differ by effortMinSpeedGain (2.0 m/s).
        val series = Arb.triple(Arb.numericDouble(0.0, 8.0), Arb.list(Arb.numericDouble(-0.6, 0.6), 30..300), Arb.int(0..1))

        checkAll(series) { (level, noise, everyOther) ->
            val records = noise.mapIndexedNotNull { t, n ->
                if (everyOther == 1 && t % 3 == 1) null else record(t, speed = maxOf(0.0, level + n))
            }
            val samples = smooth(buildGrid(rawSession(records), params), params)

            detectEfforts(samples, params) shouldBe emptyList()
        }
    }
}
