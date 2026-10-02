package com.ttaaa.ultimate.analysis

import com.ttaaa.ultimate.analysis.grid.Grid
import com.ttaaa.ultimate.analysis.grid.buildGrid
import com.ttaaa.ultimate.analysis.metrics.movingSpeed
import com.ttaaa.ultimate.analysis.metrics.timeMetrics
import com.ttaaa.ultimate.domain.AnalysisParameters
import com.ttaaa.ultimate.domain.RawSession
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

        Golden.verify("$fileName.analysis.txt", summary(raw, buildGrid(raw, params), analyze(raw, params)))
    }

    private fun summary(raw: RawSession, grid: Grid, result: AnalysisResult) = buildString {
        val points = grid.points
        val present = points.mapTo(HashSet()) { it.t }
        val gaps = (0..grid.lastT).filterNot { it in present }.fold(mutableListOf<IntRange>()) { ranges, t ->
            if (ranges.lastOrNull()?.last == t - 1) ranges[ranges.lastIndex] = ranges.last().first..t else ranges += t..t
            ranges
        }
        val samples = result.samples
        val pauses = result.pauses
        val efforts = result.efforts

        appendLine("analysisVersion: $ANALYSIS_VERSION")
        appendLine("recordingMode: ${result.recordingMode}")
        appendLine("lastT: ${result.lastT}")
        appendLine("samples: ${points.size}, interpolated: ${points.count { it.interpolated }}")
        appendLine("speed outliers: ${raw.records.count { (it.speed ?: 0.0) > params.maxPlausibleSpeed }}")
        appendLine("gaps: ${gaps.sumOf { it.count() }} s in ${gaps.size}: ${gaps.joinToString { "[${it.first}, ${it.last}]" }}")
        appendLine("grid sha256: ${Golden.sha256(points.joinToString("\n"))}")
        appendLine("smoothed speed: max ${samples.maxOf { it.speed }.format()} m/s")
        appendLine("GPS acceleration: min ${samples.minOf { it.accel }.format()}, max ${samples.maxOf { it.accel }.format()} m/s²")
        val session = TimeRange(0, result.lastT)
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
        val window = result.sessionMetrics
        appendLine(
            "distance: ${window.distance.distanceM.format()} m (watch: ${raw.totalDistanceM?.format()} m), " +
                "active ${window.distance.activeDistanceM.format()} m",
        )
        appendLine("speed zones (s): ${window.zones.joinToString { "${it.zone} ${it.timeSec}" }}")
        appendLine("decelerations: ${window.decelCount}, fatigue: ${window.fatigue.peakSpeedDropPct?.format()} %")
        appendLine(
            "heart rate: avg ${window.heartRate.avg?.format()}, max ${window.heartRate.max} bpm, " +
                "zones % ${window.heartRate.zonesPct.map { it.format() }}",
        )
        appendLine("session metrics sha256: ${Golden.sha256(window.toString())}")
        appendLine("laps: ${result.laps.joinToString { "[${it.range.fromT}, ${it.range.toT}] ${it.trigger}" }}")
        appendLine("reference position: ${result.referencePosition?.let { "${it.lat.format(5)}, ${it.lon.format(5)}" }}")
    }

    private fun Double.format(decimals: Int = 3) = String.format(Locale.ROOT, "%.${decimals}f", this)

    companion object {
        @JvmStatic
        fun goldenFiles(): List<String> = Golden.fitFileNames
    }
}
