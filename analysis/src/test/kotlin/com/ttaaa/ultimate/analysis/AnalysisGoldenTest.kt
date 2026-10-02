package com.ttaaa.ultimate.analysis

import com.ttaaa.ultimate.analysis.grid.Grid
import com.ttaaa.ultimate.analysis.grid.buildGrid
import com.ttaaa.ultimate.domain.AnalysisParameters
import com.ttaaa.ultimate.domain.RawSession
import com.ttaaa.ultimate.fit.FitParser
import com.ttaaa.ultimate.fit.Golden
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Files

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

        Golden.verify("$fileName.analysis.txt", summary(raw, grid))
    }

    private fun summary(raw: RawSession, grid: Grid): String = buildString {
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
    }

    companion object {
        @JvmStatic
        fun goldenFiles(): List<String> = Golden.fitFileNames
    }
}
