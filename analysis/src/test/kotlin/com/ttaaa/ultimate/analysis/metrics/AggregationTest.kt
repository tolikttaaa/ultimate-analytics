package com.ttaaa.ultimate.analysis.metrics

import com.ttaaa.ultimate.analysis.RawSessions.effort
import com.ttaaa.ultimate.analysis.RawSessions.sample
import com.ttaaa.ultimate.domain.AnalysisParameters
import com.ttaaa.ultimate.domain.Effort
import com.ttaaa.ultimate.domain.EffortStats
import com.ttaaa.ultimate.domain.MeanAndBest
import com.ttaaa.ultimate.domain.TimeRange
import com.ttaaa.ultimate.domain.WindowMetrics
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.numericDouble
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.math.abs

class AggregationTest {

    private val params = AnalysisParameters()

    @Test
    fun `weights effort means by effort count and keeps the best`() {
        val samples = (0..99).map { sample(it, 3.0, hr = 120) }
        val efforts = listOf(
            effort(5, peakSpeed = 6.0, timeTo80PctPeakSec = 2.0),
            effort(15, peakSpeed = 6.0, timeTo80PctPeakSec = 3.0),
            effort(60, peakSpeed = 9.0, timeTo80PctPeakSec = 2.5),
        )
        val first = windowMetrics(samples, efforts, TimeRange(0, 50), params)
        val second = windowMetrics(samples, efforts, TimeRange(50, 100), params)

        val total = aggregateMetrics(listOf(first, second))

        total.range shouldBe null
        total.time.elapsedSec shouldBe 100
        total.efforts.count shouldBe 3
        total.efforts.peakSpeed shouldBe MeanAndBest(7.0, 9.0)
        total.efforts.timeTo80PctPeakSec shouldBe MeanAndBest(2.5, 2.0)
        total.heartRate.avg shouldBe 120.0
    }

    @Test
    fun `totals of nothing are zero`() {
        val total = aggregateMetrics(emptyList())

        total.time.elapsedSec shouldBe 0
        total.efforts.count shouldBe 0
        total.efforts.peakSpeed shouldBe null
        total.distance.movingSpeed shouldBe null
        total.heartRate.zonesPct shouldBe emptyList()
    }

    @Test
    fun `adjacent windows add up to the whole window, and so do their totals`() = runTest {
        val sessions = arbitrary {
            val length = Arb.int(1..300).bind()
            // Per second: gap, active or paused, with speed, acceleration and heart rate.
            val samples = (0..<length).mapNotNull { t ->
                when (Arb.int(0..5).bind()) {
                    0 -> null
                    else -> sample(
                        t,
                        speed = Arb.numericDouble(0.0, 9.0).bind(),
                        inPause = Arb.int(0..3).bind() == 0,
                        accel = Arb.numericDouble(-3.0, 3.0).bind(),
                        hr = Arb.int(80..200).bind(),
                    )
                }
            }
            val efforts = (0..<length).filter { Arb.int(0..9).bind() == 0 }.map { startT ->
                effort(
                    startT,
                    peakSpeed = Arb.numericDouble(4.5, 9.0).bind(),
                    meanSpeed = Arb.numericDouble(2.0, 7.0).bind(),
                    meanAccel = Arb.numericDouble(0.5, 3.0).bind(),
                    peakAccel = Arb.numericDouble(0.5, 4.0).bind(),
                    meanSpeedFirst3s = Arb.numericDouble(1.0, 6.0).bind(),
                    timeTo80PctPeakSec = Arb.numericDouble(0.5, 4.0).bind(),
                )
            }
            val cuts = List(3) { Arb.int(0..length + 10).bind() }.sorted()
            Triple(samples, efforts, cuts)
        }

        checkAll(sessions) { (samples, efforts, cuts) ->
            val (a, b, c) = cuts
            val first = windowMetrics(samples, efforts, TimeRange(a, b), params)
            val second = windowMetrics(samples, efforts, TimeRange(b, c), params)
            val whole = windowMetrics(samples, efforts, TimeRange(a, c), params)

            aggregateMetrics(listOf(first, second)).shouldMatch(whole)
        }
    }

    /** Everything that adding up two adjacent windows must reproduce (moving speed and fatigue are not additive). */
    private fun WindowMetrics.shouldMatch(whole: WindowMetrics) {
        time shouldBe whole.time.copy(workRestRatio = time.workRestRatio)
        time.workRestRatio.close(whole.time.workRestRatio)
        distance.distanceM.close(whole.distance.distanceM)
        distance.activeDistanceM.close(whole.distance.activeDistanceM)
        distance.maxSpeed shouldBe whole.distance.maxSpeed
        zones.map { it.zone to it.timeSec } shouldBe whole.zones.map { it.zone to it.timeSec }
        zones.zip(whole.zones).forEach { (part, all) -> part.distanceM.close(all.distanceM) }
        decelCount shouldBe whole.decelCount
        efforts.count shouldBe whole.efforts.count
        efforts.perActiveMin.close(whole.efforts.perActiveMin)
        listOf<(EffortStats) -> MeanAndBest?>(
            { it.peakSpeed }, { it.meanSpeed }, { it.meanAccel }, { it.peakAccel }, { it.meanSpeedFirst3s },
            { it.timeTo80PctPeakSec },
        ).forEach { stat ->
            stat(efforts)?.mean.close(stat(whole.efforts)?.mean)
            stat(efforts)?.best shouldBe stat(whole.efforts)?.best
        }
        heartRate.avg.close(whole.heartRate.avg)
        heartRate.max shouldBe whole.heartRate.max
        heartRate.zonesPct.size shouldBe whole.heartRate.zonesPct.size
        heartRate.zonesPct.zip(whole.heartRate.zonesPct).forEach { (part, all) -> part.close(all) }
    }

    private fun Double?.close(expected: Double?) {
        if (this == null || expected == null) {
            this shouldBe expected
        } else {
            this shouldBe (expected plusOrMinus 1e-9 * (1 + abs(expected)))
        }
    }
}
