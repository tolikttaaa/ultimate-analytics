package com.ttaaa.ultimate.app.web

import com.ttaaa.ultimate.app.drilltype.DrillTypeService
import com.ttaaa.ultimate.app.session.SessionFilter
import com.ttaaa.ultimate.domain.DrillKind
import com.ttaaa.ultimate.domain.DrillType
import com.ttaaa.ultimate.domain.Surface
import com.ttaaa.ultimate.domain.WindowMetrics
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

/** A new drill type. The code is stored in upper case (`[A-Z0-9_]+`); the colour is `#RRGGBB`. */
data class DrillTypeCreate(val code: String, val name: String, val color: String, val kind: DrillKind)

data class DrillTypePatch(
    val code: String? = null,
    val name: String? = null,
    val color: String? = null,
    val kind: DrillKind? = null,
)

data class DrillTypeSessionRow(
    val sessionId: UUID,
    val startTime: Instant,
    val surface: Surface,
    val segmentCount: Int,
    val metrics: WindowMetrics,
)

/** Totals across all matching sessions and one row per session in time order (spec 9.1, 10.1 screen 5). */
data class DrillTypeStatsDto(val drillType: DrillType, val totals: WindowMetrics, val sessions: List<DrillTypeSessionRow>)

@RestController
@RequestMapping("/api/drill-types")
@Tag(name = "Drill types")
class DrillTypeController(private val drillTypes: DrillTypeService) {

    @GetMapping
    fun list(): List<DrillType> = drillTypes.list()

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a drill type; 409 if the code exists")
    fun create(@RequestBody body: DrillTypeCreate): DrillType = drillTypes.create(body.code, body.name, body.color, body.kind)

    @PatchMapping("/{id}")
    fun update(@PathVariable id: UUID, @RequestBody body: DrillTypePatch): DrillType =
        drillTypes.update(id, body.code, body.name, body.color, body.kind)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a drill type; its segments become untyped")
    fun delete(@PathVariable id: UUID) = drillTypes.delete(id)

    @GetMapping("/{id}/stats")
    @Operation(summary = "Metrics of all segments of the drill type, optionally filtered by session start time and surface")
    fun stats(
        @PathVariable id: UUID,
        @RequestParam(required = false) from: Instant?,
        @RequestParam(required = false) to: Instant?,
        @RequestParam(required = false) surface: Surface?,
    ): DrillTypeStatsDto {
        val stats = drillTypes.stats(id, SessionFilter(from, to, surface))
        return DrillTypeStatsDto(
            drillType = stats.drillType,
            totals = stats.totals,
            sessions = stats.sessions.map {
                DrillTypeSessionRow(it.session.id, it.session.startTime, it.session.surface, it.segmentCount, it.metrics)
            },
        )
    }
}
