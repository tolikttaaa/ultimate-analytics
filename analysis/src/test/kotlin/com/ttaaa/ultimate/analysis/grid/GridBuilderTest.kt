package com.ttaaa.ultimate.analysis.grid

import com.ttaaa.ultimate.analysis.RawSessions.T0
import com.ttaaa.ultimate.analysis.RawSessions.everySecond
import com.ttaaa.ultimate.analysis.RawSessions.rawSession
import com.ttaaa.ultimate.analysis.RawSessions.record
import com.ttaaa.ultimate.analysis.RawSessions.timer
import com.ttaaa.ultimate.domain.AnalysisParameters
import com.ttaaa.ultimate.domain.GeoPoint
import com.ttaaa.ultimate.domain.RecordingMode
import com.ttaaa.ultimate.domain.TimerEventType.START
import com.ttaaa.ultimate.domain.TimerEventType.STOP
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.numericDouble
import io.kotest.property.arbitrary.pair
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class GridBuilderTest {

    private val params = AnalysisParameters()

    @Test
    fun `records every second become one point each`() {
        val grid = buildGrid(rawSession(everySecond(0..9)), params)

        grid.recordingMode shouldBe RecordingMode.EVERY_SECOND
        grid.startTime shouldBe T0
        grid.lastT shouldBe 9
        grid.points.map { it.t } shouldBe (0..9).toList()
        grid.points.none { it.interpolated } shouldBe true
        grid.points[3].timestamp shouldBe T0.plusSeconds(3)
    }

    @Test
    fun `sorts records and keeps the last of duplicate seconds`() {
        val records = listOf(record(2), record(0), record(1, speed = 1.0), record(1, speed = 2.0))

        buildGrid(rawSession(records), params).points.map { it.t to it.speed } shouldBe
            listOf(0 to 3.0, 1 to 2.0, 2 to 3.0)
    }

    @Test
    fun `fills gaps of up to maxInterpolationGapSec linearly`() {
        val left = record(20, speed = 2.0, hr = 100, position = GeoPoint(34.0, 33.0), distanceM = 100.0)
        val right = record(24, speed = 6.0, hr = 140, position = GeoPoint(34.004, 33.008), distanceM = 116.0)
        val grid = buildGrid(rawSession(everySecond(0..19) + left + right + everySecond(25..45)), params)

        val filled = grid.points.filter { it.t in 21..23 }
        filled.map { it.speed } shouldBe listOf(3.0, 4.0, 5.0)
        filled.map { it.hr } shouldBe listOf(110, 120, 130)
        filled.map { it.distanceM } shouldBe listOf(104.0, 108.0, 112.0)
        filled[1].position!!.lat shouldBe (34.002 plusOrMinus 1e-12)
        filled[1].position!!.lon shouldBe (33.004 plusOrMinus 1e-12)
        filled.all { it.interpolated && it.speedRaw == null } shouldBe true
        grid.runs() shouldBe listOf(0..45)
    }

    @Test
    fun `keeps longer gaps`() {
        val grid = buildGrid(rawSession(everySecond(0..20) + everySecond(25..45)), params)

        grid.points.map { it.t } shouldBe (0..20).toList() + (25..45).toList()
        grid.runs() shouldBe listOf(0..20, 21..41)
    }

    @Test
    fun `never interpolates across a timer stop`() {
        val events = listOf(timer(0, START), timer(21, STOP), timer(22, START))
        val grid = buildGrid(rawSession(everySecond(0..20) + everySecond(23..45), events), params)

        grid.points.map { it.t } shouldBe (0..20).toList() + (23..45).toList()
    }

    @Test
    fun `replaces a speed outlier by interpolation and keeps what was recorded`() {
        val records = everySecond(0..20, speed = 4.0).toMutableList()
        records[10] = record(10, speed = 25.0, hr = 150)

        val point = buildGrid(rawSession(records), params).points.single { it.t == 10 }

        point.speed shouldBe 4.0
        point.speedRaw shouldBe 25.0
        point.hr shouldBe 150
        point.interpolated shouldBe true
    }

    @Test
    fun `an outlier run longer than the gap limit becomes a gap`() {
        val records = (0..30).map { record(it, speed = if (it in 12..15) 20.0 else 3.0) }

        buildGrid(rawSession(records), params).points.map { it.t } shouldBe (0..11).toList() + (16..30).toList()
    }

    @Test
    fun `seconds without a speed at the start are a gap and t = 0 stays at the first record`() {
        val records = listOf(record(0, speed = null), record(1, speed = null)) + everySecond(2..20)

        val grid = buildGrid(rawSession(records), params)

        grid.startTime shouldBe T0
        grid.points.first().t shouldBe 2
        grid.lastT shouldBe 20
    }

    @Test
    fun `detects Smart recording and interpolates its longer intervals`() {
        // Records 1-6 s apart like the golden files, then 9 missing seconds.
        val ts = listOf(0, 1, 3, 6, 10, 15, 21, 22, 24, 34)

        val grid = buildGrid(rawSession(ts.map { record(it) }), params)

        grid.recordingMode shouldBe RecordingMode.SMART
        grid.points.map { it.t } shouldBe (0..24).toList() + 34
    }

    @Test
    fun `recording mode ignores intervals across a timer stop`() {
        val records = everySecond(0..5) + everySecond(20..25) + everySecond(40..45)
        val events = listOf(timer(5, STOP), timer(20, START), timer(25, STOP), timer(40, START))

        buildGrid(rawSession(records, events), params).recordingMode shouldBe RecordingMode.EVERY_SECOND
        buildGrid(rawSession(records), params).recordingMode shouldBe RecordingMode.SMART
    }

    @Test
    fun `keeps plausible recorded speeds and fills exactly the short gaps between them`() = runTest {
        val steps = Arb.list(Arb.pair(Arb.int(1..10), Arb.numericDouble(0.0, 13.0)), 2..120)

        checkAll(steps) { intervalsAndSpeeds ->
            var t = 0
            val records = intervalsAndSpeeds.map { (interval, speed) -> record(t, speed).also { t += interval } }
            val grid = buildGrid(rawSession(records), params)
            val maxGapSec = when (grid.recordingMode) {
                RecordingMode.EVERY_SECOND -> params.maxInterpolationGapSec
                RecordingMode.SMART -> params.smartRecordingMaxGapSec
            }
            val recorded = grid.points.filterNot { it.interpolated }
            val ts = grid.points.map { it.t }

            ts.zipWithNext().all { (a, b) -> a < b } shouldBe true
            recorded.map { it.speed } shouldBe records.mapNotNull { it.speed }.filter { it <= params.maxPlausibleSpeed }
            recorded.zipWithNext().forEach { (left, right) ->
                val between = (left.t + 1)..<right.t
                if (right.t - left.t - 1 <= maxGapSec) {
                    ts shouldContainAll between.toList()
                    grid.points.filter { it.t in between }.forEach {
                        it.speed shouldBeGreaterThanOrEqual minOf(left.speed, right.speed) - 1e-9
                        it.speed shouldBeLessThanOrEqual maxOf(left.speed, right.speed) + 1e-9
                    }
                } else {
                    ts.none { it in between } shouldBe true
                }
            }
        }
    }
}
