package com.ttaaa.ultimate.app.web

import com.ttaaa.ultimate.analysis.ANALYSIS_VERSION
import com.ttaaa.ultimate.app.metrics.RecomputeService
import com.ttaaa.ultimate.domain.AnalysisParameters
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class RecomputeOutdatedResult(val count: Int)

/** The current analysis parameters and the version they belong to (spec 6.2, 8.2). */
data class AnalysisParametersDto(val analysisVersion: Int, val parameters: AnalysisParameters)

@RestController
@Tag(name = "Recompute")
class RecomputeController(
    private val recompute: RecomputeService,
    private val views: SessionViews,
    private val params: AnalysisParameters,
) {

    @PostMapping("/api/sessions/{id}/recompute")
    @Operation(summary = "Analyse the session again from its raw file; segments, notes and a manual surface stay")
    fun recompute(@PathVariable id: UUID): SessionDetail = views.detail(recompute.recompute(id))

    @PostMapping("/api/sessions/recompute-outdated")
    @Operation(summary = "Recompute every session analysed with an older analysis version")
    fun recomputeOutdated(): RecomputeOutdatedResult = RecomputeOutdatedResult(recompute.recomputeOutdated())

    @GetMapping("/api/analysis/parameters")
    @Operation(summary = "The current analysis parameters and analysis version (read-only)")
    fun parameters(): AnalysisParametersDto = AnalysisParametersDto(ANALYSIS_VERSION, params)
}
