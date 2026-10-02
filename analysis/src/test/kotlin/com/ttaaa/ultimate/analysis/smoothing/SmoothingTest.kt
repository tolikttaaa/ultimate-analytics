package com.ttaaa.ultimate.analysis.smoothing

import com.ttaaa.ultimate.analysis.RawSessions.T0
import com.ttaaa.ultimate.analysis.grid.Grid
import com.ttaaa.ultimate.analysis.grid.GridPoint
import com.ttaaa.ultimate.domain.AnalysisParameters
import com.ttaaa.ultimate.domain.GeoPoint
import com.ttaaa.ultimate.domain.RecordingMode
import com.ttaaa.ultimate.domain.Sample
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SmoothingTest {

    private val params = AnalysisParameters() // window 5, order 2

    private fun point(t: Int, speed: Double) =
        GridPoint(t, T0.plusSeconds(t.toLong()), null, speed, speed, null, null, null, interpolated = false)

    /** A grid of runs, each given as its first `t` and its speeds. */
    private fun grid(vararg runs: Pair<Int, List<Double>>): Grid {
        val points = runs.flatMap { (from, speeds) -> speeds.mapIndexed { i, speed -> point(from + i, speed) } }
        return Grid(T0, lastT = points.last().t, RecordingMode.EVERY_SECOND, points)
    }

    private fun List<Sample>.speeds() = map { it.speed }

    private infix fun List<Double>.shouldBeCloseTo(expected: List<Double>) {
        size shouldBe expected.size
        zip(expected).forEach { (actual, wanted) -> actual shouldBe (wanted plusOrMinus 1e-9) }
    }

    @Test
    fun `keeps a constant speed and has no acceleration`() {
        val samples = smooth(grid(0 to List(20) { 4.0 }), params)

        samples.speeds() shouldBeCloseTo List(20) { 4.0 }
        samples.map { it.accel } shouldBeCloseTo List(20) { 0.0 }
    }

    @Test
    fun `reproduces a quadratic speed curve and its slope, also at the ends of the run`() {
        val ts = 0..19
        val samples = smooth(grid(0 to ts.map { 0.05 * it * it + 0.5 * it + 1.0 }), params)

        samples.speeds() shouldBeCloseTo ts.map { 0.05 * it * it + 0.5 * it + 1.0 }
        samples.map { it.accel } shouldBeCloseTo ts.map { 0.1 * it + 0.5 }
    }

    @Test
    fun `clamps negative smoothed speeds to zero but not the acceleration`() {
        val samples = smooth(grid(0 to listOf(0.0, 0.0, 0.0, 0.0, 5.0, 0.0, 0.0, 0.0, 0.0)), params)

        // Centred window at t = 2: speed (-3·0 + 12·0 + 17·0 + 12·0 - 3·5) / 35 < 0, slope (2·5) / 10.
        samples[2].speed shouldBe 0.0
        samples[2].accel shouldBe (1.0 plusOrMinus 1e-12)
    }

    @Test
    fun `smooths every run on its own`() {
        val samples = smooth(grid(0 to List(10) { 2.0 }, 15 to List(10) { 6.0 }), params)

        samples.speeds() shouldBeCloseTo List(10) { 2.0 } + List(10) { 6.0 }
        samples.map { it.accel } shouldBeCloseTo List(20) { 0.0 }
    }

    @Test
    fun `fits runs shorter than the window as a whole`() {
        val samples = smooth(grid(0 to listOf(3.0), 10 to listOf(3.0, 5.0), 20 to listOf(1.0, 2.0, 5.0)), params)

        samples.speeds() shouldBeCloseTo listOf(3.0, 3.0, 5.0, 1.0, 2.0, 5.0)
        // Single sample: no slope. Two samples: a line. Three samples: the parabola through them, y = 1 + t².
        samples.map { it.accel } shouldBeCloseTo listOf(0.0, 2.0, 2.0, 0.0, 2.0, 4.0)
    }

    @Test
    fun `carries the grid values over`() {
        val point = GridPoint(
            t = 7, timestamp = T0.plusSeconds(7), position = GeoPoint(34.7, 33.1), speedRaw = null, speed = 3.0,
            hr = 150, distanceM = 20.0, altitudeM = 4.0, interpolated = true,
        )

        val sample = smooth(Grid(T0, lastT = 7, RecordingMode.SMART, listOf(point)), params).single()

        sample shouldBe Sample(
            t = 7, timestamp = T0.plusSeconds(7), position = GeoPoint(34.7, 33.1), speedRaw = null, speed = 3.0,
            accel = 0.0, hr = 150, distanceM = 20.0, altitudeM = 4.0, interpolated = true, inPause = false,
        )
    }
}
