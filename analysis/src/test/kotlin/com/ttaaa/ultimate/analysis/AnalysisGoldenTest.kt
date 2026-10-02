package com.ttaaa.ultimate.analysis

import com.ttaaa.ultimate.analysis.effort.detectEfforts
import com.ttaaa.ultimate.analysis.grid.Grid
import com.ttaaa.ultimate.analysis.grid.buildGrid
import com.ttaaa.ultimate.analysis.metrics.movingSpeed
import com.ttaaa.ultimate.analysis.metrics.timeMetrics
import com.ttaaa.ultimate.analysis.pause.detectPauses
import com.ttaaa.ultimate.analysis.pause.markPauses
import com.ttaaa.ultimate.analysis.smoothing.smooth
import com.ttaaa.ultimate.domain.AnalysisParameters
import com.ttaaa.ultimate.domain.Effort
import com.ttaaa.ultimate.domain.RawSession
import com.ttaaa.ultimate.domain.Sample
import com.ttaaa.ultimate.domain.TimeRange
import com.ttaaa.ultimate.fit.FitParser
import com.ttaaa.ultimate.fit.Golden
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Files
import java.util.Locale

/**
 * Golden files through the analysis pipeline (spec 12). Any change in the output fails until the snapshot is updated
 * on purpose (`-PupdateGolden`), together with `ANALYSIS_VERSION` when results change.
 */
class AnalysisGoldenTest {

    private val params = AnalysisParameters()

    @ParameterizedTest(name = "{0}")
    @MethodSource("goldenFiles")
    fun `analyses the golden file as approved`(fileName: String) {
        val raw = Files.newInputStream(Golden.fitFile(fileName)).use(FitParser::parse)
        val grid = buildGrid(raw, params)
        val smoothed = smooth(grid, params)
        val pauses = detectPauses(smoothed, params)
        val samples = markPauses(smoothed, pauses)
        val efforts = detectEfforts(samples, params)

        Golden.verify("$fileName.analysis.txt", summary(raw, grid, samples, pauses, efforts))
    }

    private fun summary(
        raw: RawSession,
        grid: Grid,
        samples: List<Sample>,
        pauses: List<TimeRange>,
        efforts: List<Effort>,
    ) = buildString {
        val points = grid.points
        val present = points.mapTo(HashSet()) { it.t }
        val gaps = (0..grid.lastT).filterNot { it in present }.fold(mutableListOf<IntRange>()) { ranges, t ->
            if (ranges.lastOrNull()?.last == t - 1) ranges[ranges.lastIndex] = ranges.last().first..t else ranges += t..t
            ranges
        }
        appendLine("analysisVersion: $ANALYSIS_VERSION")
        appendLine("recordingMode: ${grid.recordingMode}")
        appendLine("lastT: ${grid.lastT}")
        appendLine("samples: ${points.size}, interpolated: ${points.count { it.interpolated }}")
        appendLine("speed outliers: ${raw.records.count { (it.speed ?: 0.0) > params.maxPlausibleSpeed }}")
        appendLine("gaps: ${gaps.sumOf { it.count() }} s in ${gaps.size}: ${gaps.joinToString { "[${it.first}, ${it.last}]" }}")
        appendLine("grid sha256: ${Golden.sha256(points.joinToString("\n"))}")
        appendLine("smoothed speed: max ${samples.maxOf { it.speed }.format()} m/s")
        appendLine("GPS acceleration: min ${samples.minOf { it.accel }.format()}, max ${samples.maxOf { it.accel }.format()} m/s²")
        val session = TimeRange(0, grid.lastT)
        val time = timeMetrics(samples, session)
        appendLine("pauses: ${pauses.size}, longest ${pauses.maxOfOrNull { it.durationSec } ?: 0} s")
        appendLine("time: elapsed ${time.elapsedSec} s, active ${time.activeSec} s, paused ${time.pausedSec} s, gap ${time.gapSec} s")
        appendLine("moving speed: ${movingSpeed(samples, session, params)?.format()} m/s")
        appendLine("samples sha256: ${Golden.sha256(samples.joinToString("\n"))}")
        val metrics = efforts.map { it.metrics }
        appendLine(
            "efforts: ${efforts.size}, peak speed mean ${metrics.map { it.peakSpeed }.average().format()} " +
                "max ${metrics.maxOfOrNull { it.peakSpeed }?.format()} m/s, " +
                "duration mean ${metrics.map { it.durationSec }.average().format()} s",
        )
        appendLine(
            "first 3 s: mean speed ${metrics.mapNotNull { it.meanSpeedFirst3s }.average().format()} m/s, " +
                "time to 80 % of peak ${metrics.map { it.timeTo80PctPeakSec }.average().format()} s",
        )
        appendLine("efforts sha256: ${Golden.sha256(efforts.joinToString("\n"))}")
    }

    private fun Double.format() = String.format(Locale.ROOT, "%.3f", this)

    companion object {
        @JvmStatic
        fun goldenFiles(): List<String> = Golden.fitFileNames
    }
}
