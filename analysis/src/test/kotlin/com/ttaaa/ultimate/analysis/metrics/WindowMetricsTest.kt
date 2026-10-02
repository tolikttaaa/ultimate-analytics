package com.ttaaa.ultimate.analysis.metrics

import com.ttaaa.ultimate.analysis.RawSessions.effort
import com.ttaaa.ultimate.analysis.RawSessions.sample
import com.ttaaa.ultimate.domain.AnalysisParameters
import com.ttaaa.ultimate.domain.Effort
import com.ttaaa.ultimate.domain.MeanAndBest
import com.ttaaa.ultimate.domain.Sample
import com.ttaaa.ultimate.domain.SpeedZone
import com.ttaaa.ultimate.domain.TimeRange
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class WindowMetricsTest {

    private val params = AnalysisParameters()

    private fun metrics(samples: List<Sample>, range: TimeRange, efforts: List<Effort> = emptyList()) =
        windowMetrics(samples, efforts, range, params)

    @Test
    fun `sorts every second into its speed zone, upper bounds exclusive`() {
        val speeds = listOf(0.5, 1.0, 1.99, 2.0, 4.0, 5.5, 6.99, 7.0, 9.0)
        val zones = metrics(speeds.mapIndexed { t, speed -> sample(t, speed) }, TimeRange(0, 9)).zones

        zones.map { it.zone to it.timeSec } shouldBe listOf(
            SpeedZone.STAND to 1, SpeedZone.WALK to 2, SpeedZone.JOG to 1,
            SpeedZone.RUN to 1, SpeedZone.HIGH_SPEED to 2, SpeedZone.SPRINT to 2,
        )
        zones.single { it.zone == SpeedZone.SPRINT }.distanceM shouldBe 16.0
        zones.single { it.zone == SpeedZone.STAND }.distanceM shouldBe 0.0 // below minDistanceSpeed
    }

    @Test
    fun `distances add up the speed of every moving second, active distance leaves out pauses`() {
        val samples = listOf(
            sample(0, 3.0),
            sample(1, 5.0),
            sample(2, 1.0, inPause = true),
            sample(3, 4.0),
            sample(4, 0.6, inPause = true), // standing: below minDistanceSpeed, no distance
        )

        with(metrics(samples, TimeRange(0, 5)).distance) {
            distanceM shouldBe 13.0
            activeDistanceM shouldBe 12.0
            movingSpeed shouldBe 4.0
            movingPaceSecPerKm shouldBe 250.0
            maxSpeed shouldBe 5.0
        }
        metrics(samples, TimeRange(10, 20)).distance.maxSpeed shouldBe null
    }

    @Test
    fun `efforts belong to the window that contains their start`() {
        val samples = (0..59).map { sample(it, 3.0) }
        val efforts = listOf(
            effort(10, peakSpeed = 6.0, timeTo80PctPeakSec = 2.5, meanSpeedFirst3s = null),
            effort(20, peakSpeed = 8.0, timeTo80PctPeakSec = 1.5, meanSpeedFirst3s = 5.0),
            effort(30, peakSpeed = 9.0),
        )

        with(metrics(samples, TimeRange(10, 30), efforts).efforts) {
            count shouldBe 2
            perActiveMin shouldBe 6.0 // 2 efforts in 20 active seconds
            peakSpeed shouldBe MeanAndBest(7.0, 8.0)
            timeTo80PctPeakSec shouldBe MeanAndBest(2.0, 1.5) // lower is better
            meanSpeedFirst3s shouldBe MeanAndBest(5.0, 5.0) // nulls are left out
        }
        metrics(samples, TimeRange(40, 50), efforts).efforts.peakSpeed shouldBe null
    }

    @Test
    fun `counts decelerations where they start, and a gap ends one`() {
        val accels = listOf(0.0, -2.0, -2.0, 0.0, -1.5, 0.0, -3.0)
        val samples = accels.mapIndexed { t, accel -> sample(t, 3.0, accel = accel) } +
            listOf(sample(10, 3.0, accel = -2.0), sample(12, 3.0, accel = -2.0))

        metrics(samples, TimeRange(0, 7)).decelCount shouldBe 3
        metrics(samples, TimeRange(2, 7)).decelCount shouldBe 2 // the run at t = 1..2 starts before the window
        metrics(samples, TimeRange(10, 13)).decelCount shouldBe 2
    }

    @Test
    fun `fatigue compares the first and the last third of the efforts`() {
        val samples = (0..99).map { sample(it, 3.0) }
        val peaks = listOf(8.0, 8.0, 7.0, 7.0, 7.0, 6.0, 6.0)
        val efforts = peaks.mapIndexed { i, peak -> effort(i * 10, peakSpeed = peak) }

        // 7 efforts: thirds of 2, (8 - 6) / 8 = 25 %.
        metrics(samples, TimeRange(0, 100), efforts).fatigue.peakSpeedDropPct shouldBe 25.0
        metrics(samples, TimeRange(0, 100), efforts.take(5)).fatigue.peakSpeedDropPct shouldBe null
    }

    @Test
    fun `heart rate zones are shares of the seconds with heart rate`() {
        // hrMax 190: zones start at 95, 114, 133, 152 and 171 bpm; 90 bpm is below the first zone.
        val heartRates = listOf(90, 95, 120, 140, 160, 180, null)
        val samples = heartRates.mapIndexed { t, hr -> sample(t, 3.0, hr = hr) }

        with(metrics(samples, TimeRange(0, 7)).heartRate) {
            avg!! shouldBe (785.0 / 6 plusOrMinus 1e-9)
            max shouldBe 180
            zonesPct.forEach { it shouldBe (100.0 / 6 plusOrMinus 1e-9) }
        }
        metrics(listOf(sample(0, 3.0)), TimeRange(0, 1)).heartRate.zonesPct shouldBe emptyList()
    }
}
