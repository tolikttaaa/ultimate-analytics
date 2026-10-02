package com.ttaaa.ultimate.app.web

import com.ttaaa.ultimate.app.metrics.MetricsService
import com.ttaaa.ultimate.app.session.EffortRepository
import com.ttaaa.ultimate.app.session.SampleRepository
import com.ttaaa.ultimate.app.session.SessionService
import com.ttaaa.ultimate.app.session.StoredEffort
import com.ttaaa.ultimate.domain.EffortMetrics
import com.ttaaa.ultimate.domain.Sample
import com.ttaaa.ultimate.domain.TimeRange
import com.ttaaa.ultimate.domain.WindowMetrics
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

/**
 * The 1 Hz series of a session in columns, ready for the charts (spec 9.2): one entry per second `t = 0..lastT`,
 * all arrays of equal length, `null` in gaps. Speeds in m/s, GPS acceleration in m/s², heart rate in bpm.
 *
 * springdoc drops the nullability of list elements, so the annotations state it for the generated TypeScript types.
 */
data class SessionSeries(
    val sessionId: UUID,
    val startTime: Instant,
    val t: List<Int>,
    @field:ArraySchema(schema = Schema(types = ["number", "null"], format = "float"))
    val speed: List<Float?>,
    @field:ArraySchema(schema = Schema(types = ["number", "null"], format = "float"))
    val speedRaw: List<Float?>,
    @field:ArraySchema(schema = Schema(types = ["number", "null"], format = "float"))
    val accel: List<Float?>,
    @field:ArraySchema(schema = Schema(types = ["integer", "null"], format = "int32"))
    val hr: List<Int?>,
    @field:ArraySchema(schema = Schema(types = ["number", "null"], format = "double"))
    val lat: List<Double?>,
    @field:ArraySchema(schema = Schema(types = ["number", "null"], format = "double"))
    val lon: List<Double?>,
    @field:ArraySchema(schema = Schema(types = ["boolean", "null"]))
    val inPause: List<Boolean?>,
    @field:ArraySchema(schema = Schema(types = ["boolean", "null"]))
    val interpolated: List<Boolean?>,
)

/** A detected effort (sprint) with its metrics (spec 6.4). */
data class EffortDto(
    val id: UUID,
    val startT: Int,
    val peakT: Int,
    val endT: Int,
    /** The segment containing the start of the effort. */
    val segmentId: UUID?,
    val metrics: EffortMetrics,
) {
    constructor(stored: StoredEffort) : this(
        stored.id, stored.effort.startT, stored.effort.peakT, stored.effort.endT, stored.segmentId, stored.effort.metrics,
    )
}

@RestController
@RequestMapping("/api/sessions/{id}")
@Tag(name = "Series and metrics")
class SeriesController(
    private val sessionService: SessionService,
    private val samples: SampleRepository,
    private val efforts: EffortRepository,
    private val metrics: MetricsService,
) {

    @GetMapping("/series")
    @Operation(summary = "The full 1 Hz series in columns; null marks gaps")
    fun series(@PathVariable id: UUID): SessionSeries {
        val session = sessionService.get(id)
        val byT = arrayOfNulls<Sample>(session.elapsedSec + 1)
        samples.findBySession(id, session.startTime).forEach { if (it.t < byT.size) byT[it.t] = it }
        val seconds = byT.toList()
        return SessionSeries(
            sessionId = id,
            startTime = session.startTime,
            t = seconds.indices.toList(),
            speed = seconds.map { it?.speed?.toFloat() },
            speedRaw = seconds.map { it?.speedRaw?.toFloat() },
            accel = seconds.map { it?.accel?.toFloat() },
            hr = seconds.map { it?.hr },
            lat = seconds.map { it?.position?.lat },
            lon = seconds.map { it?.position?.lon },
            inPause = seconds.map { it?.inPause },
            interpolated = seconds.map { it?.interpolated },
        )
    }

    @GetMapping("/efforts")
    @Operation(summary = "The detected efforts (sprints) with their metrics, in time order")
    fun efforts(@PathVariable id: UUID): List<EffortDto> {
        sessionService.get(id)
        return efforts.findBySession(id).map(::EffortDto)
    }

    @GetMapping("/metrics")
    @Operation(
        summary = "Metrics of the window [from, to], computed live",
        description = "`from` and `to` are seconds `t` within [0, elapsedSec]; the window covers the seconds " +
            "from..to-1, so its elapsed time is to - from. Efforts belong to it when they start in it.",
    )
    fun windowMetrics(@PathVariable id: UUID, @RequestParam from: Int, @RequestParam to: Int): WindowMetrics {
        val session = sessionService.get(id)
        require(to <= session.elapsedSec) { "to must not be after the end of the session (${session.elapsedSec})" }
        return metrics.windowMetrics(session, TimeRange(from, to))
    }
}
