package com.ttaaa.ultimate.app.web

import com.ttaaa.ultimate.app.metrics.MetricsService
import com.ttaaa.ultimate.app.segment.SegmentService
import com.ttaaa.ultimate.app.session.SessionService
import com.ttaaa.ultimate.domain.TimeRange
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
import java.util.Optional
import java.util.UUID

data class SegmentCreate(val startT: Int, val endT: Int, val drillTypeId: UUID? = null, val label: String? = null)

/** Changes to a segment; absent fields stay. `drillTypeId` and `label`: null clears them. */
data class SegmentPatch(
    val startT: Int? = null,
    val endT: Int? = null,
    val drillTypeId: Optional<UUID>? = null,
    val label: Optional<String>? = null,
)

data class SegmentSplit(val atT: Int)

data class SegmentMerge(val segmentIds: List<UUID>)

@RestController
@RequestMapping("/api/sessions/{id}/segments")
@Tag(name = "Segments")
class SegmentController(
    private val segmentService: SegmentService,
    private val sessionService: SessionService,
    private val metrics: MetricsService,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a segment; 409 if it overlaps another one")
    fun create(@PathVariable id: UUID, @RequestBody body: SegmentCreate): SegmentDto =
        SegmentDto(segmentService.create(id, TimeRange(body.startT, body.endT), body.drillTypeId, body.label))

    @PatchMapping("/{segmentId}")
    @Operation(summary = "Edit bounds, drill type or label; 409 if the new bounds overlap another segment")
    fun update(@PathVariable id: UUID, @PathVariable segmentId: UUID, @RequestBody body: SegmentPatch): SegmentDto =
        SegmentDto(segmentService.update(id, segmentId, body.startT, body.endT, body.drillTypeId, body.label))

    @DeleteMapping("/{segmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: UUID, @PathVariable segmentId: UUID) = segmentService.delete(id, segmentId)

    @PostMapping("/{segmentId}/split")
    @Operation(summary = "Split a segment at atT; both parts keep its drill type and label")
    fun split(@PathVariable id: UUID, @PathVariable segmentId: UUID, @RequestBody body: SegmentSplit): List<SegmentDto> =
        segmentService.split(id, segmentId, body.atT).map(::SegmentDto)

    @PostMapping("/merge")
    @Operation(summary = "Merge consecutive segments; the first keeps its drill type and label")
    fun merge(@PathVariable id: UUID, @RequestBody body: SegmentMerge): SegmentDto =
        SegmentDto(segmentService.merge(id, body.segmentIds))

    @PostMapping("/reset-from-laps")
    @Operation(summary = "Replace all segments with the lap-based ones; requires confirm=true")
    fun resetFromLaps(@PathVariable id: UUID, @RequestParam(defaultValue = "false") confirm: Boolean): List<SegmentDto> =
        segmentService.resetFromLaps(id, confirm).map(::SegmentDto)

    @GetMapping("/{segmentId}/metrics")
    @Operation(summary = "Metrics of the segment (cached)")
    fun metrics(@PathVariable id: UUID, @PathVariable segmentId: UUID): WindowMetrics =
        metrics.segmentMetrics(sessionService.get(id), segmentService.get(id, segmentId)).metrics
}
