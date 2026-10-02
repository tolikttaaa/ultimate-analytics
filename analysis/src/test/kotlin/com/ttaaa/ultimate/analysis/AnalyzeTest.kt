package com.ttaaa.ultimate.analysis

import com.ttaaa.ultimate.analysis.RawSessions.T0
import com.ttaaa.ultimate.analysis.RawSessions.rawSession
import com.ttaaa.ultimate.analysis.RawSessions.record
import com.ttaaa.ultimate.analysis.effort.detectEfforts
import com.ttaaa.ultimate.analysis.grid.buildGrid
import com.ttaaa.ultimate.analysis.metrics.windowMetrics
import com.ttaaa.ultimate.analysis.pause.detectPauses
import com.ttaaa.ultimate.analysis.pause.markPauses
import com.ttaaa.ultimate.analysis.smoothing.smooth
import com.ttaaa.ultimate.domain.AnalysisParameters
import com.ttaaa.ultimate.domain.GeoPoint
import com.ttaaa.ultimate.domain.Lap
import com.ttaaa.ultimate.domain.RawLap
import com.ttaaa.ultimate.domain.TimeRange
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AnalyzeTest {

    private val params = AnalysisParameters()

    // Standing, a sprint, standing again.
    private val speeds = List(30) { 0.3 } + listOf(1.0, 3.0, 5.0, 6.5, 7.0, 7.0, 6.0, 4.0, 2.0) + List(30) { 0.3 }
    private val records = speeds.mapIndexed { t, speed -> record(t, speed, hr = 120 + t / 2) }

    @Test
    fun `chains the pipeline steps`() {
        val result = analyze(rawSession(records), params)

        val grid = buildGrid(rawSession(records), params)
        val smoothed = smooth(grid, params)
        val pauses = detectPauses(smoothed, params)
        val samples = markPauses(smoothed, pauses)
        val efforts = detectEfforts(samples, params)
        result.startTime shouldBe T0
        result.lastT shouldBe speeds.lastIndex
        result.samples shouldBe samples
        result.pauses shouldBe pauses
        result.efforts shouldBe efforts
        result.efforts.size shouldBe 1
        result.sessionMetrics shouldBe windowMetrics(samples, efforts, TimeRange(0, speeds.lastIndex), params)
    }

    @Test
    fun `puts the laps on the time axis`() {
        val laps = listOf(
            RawLap(T0.plusMillis(30_000), T0.plusMillis(68_400), "session_end"),
            RawLap(T0.minusSeconds(5), T0.plusMillis(29_600), "manual"),
        )

        analyze(rawSession(records).copy(laps = laps), params).laps shouldBe listOf(
            Lap(0, TimeRange(0, 30), "manual"), // starts before the first record, ends at 29.6 s
            Lap(1, TimeRange(30, 68), "session_end"), // ends after the last record (t = 68)
        )
    }

    @Test
    fun `the reference position is the FIT start position, else the first sample position`() {
        val positioned = records.mapIndexed { t, record -> if (t >= 3) record.copy(position = GeoPoint(34.68, 33.04 + t * 1e-5)) else record }

        analyze(rawSession(positioned).copy(startPosition = GeoPoint(34.7, 33.1)), params).referencePosition shouldBe
            GeoPoint(34.7, 33.1)
        analyze(rawSession(positioned), params).referencePosition shouldBe GeoPoint(34.68, 33.04 + 3 * 1e-5)
        analyze(rawSession(records), params).referencePosition shouldBe null
    }
}
