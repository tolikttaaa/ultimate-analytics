package com.ttaaa.ultimate.app.segment

import com.ttaaa.ultimate.domain.Effort
import com.ttaaa.ultimate.domain.EffortMetrics
import com.ttaaa.ultimate.domain.Lap
import com.ttaaa.ultimate.domain.SegmentSource
import com.ttaaa.ultimate.domain.TimeRange
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.UUID

class LapSegmentsTest {

    private val sessionId = UUID.randomUUID()

    @Test
    fun `one segment per lap of at least 10 seconds`() {
        val laps = listOf(
            Lap(0, TimeRange(0, 600), "manual"),
            Lap(1, TimeRange(600, 605), "manual"), // too short
            Lap(2, TimeRange(605, 1200), "session_end"),
        )

        val segments = segmentsFromLaps(sessionId, laps)

        segments.map { Triple(it.label, it.range, it.source) } shouldBe listOf(
            Triple("Lap 1", TimeRange(0, 600), SegmentSource.LAP),
            Triple("Lap 3", TimeRange(605, 1200), SegmentSource.LAP),
        )
        segments.all { it.sessionId == sessionId && it.drillTypeId == null } shouldBe true
    }

    @Test
    fun `skips laps that overlap an earlier one`() {
        val laps = listOf(Lap(0, TimeRange(0, 600), null), Lap(1, TimeRange(500, 900), null))

        segmentsFromLaps(sessionId, laps).map { it.range } shouldBe listOf(TimeRange(0, 600))
    }

    @Test
    fun `an effort belongs to the segment containing its start`() {
        val segments = segmentsFromLaps(sessionId, listOf(Lap(0, TimeRange(0, 600), null), Lap(1, TimeRange(600, 1200), null)))
        fun effortAt(startT: Int) = Effort(startT, startT + 2, startT + 4, metrics)

        segmentOf(effortAt(599), segments) shouldBe segments[0]
        segmentOf(effortAt(600), segments) shouldBe segments[1] // a shared boundary belongs to the later segment
        segmentOf(effortAt(1200), segments) shouldBe null
    }

    private val metrics = EffortMetrics(
        startSpeed = 0.0, peakSpeed = 6.0, timeToPeakSec = 2, durationSec = 4, distanceM = 15.0, meanSpeed = 3.7,
        meanAccel = 3.0, peakAccel = 3.5, speedAt1s = 3.0, speedAt2s = 6.0, speedAt3s = 5.0, meanSpeedFirst3s = 4.7,
        distanceFirst3s = 9.0, timeTo80PctPeakSec = 1.6, maxDecelAfter = -2.0, hrStart = null, hrMax = null,
    )
}
