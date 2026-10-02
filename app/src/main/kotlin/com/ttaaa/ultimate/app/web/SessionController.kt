package com.ttaaa.ultimate.app.web

import com.ttaaa.ultimate.app.geozone.GeozoneRepository
import com.ttaaa.ultimate.app.ingestion.UploadResult
import com.ttaaa.ultimate.app.ingestion.UploadService
import com.ttaaa.ultimate.app.ingestion.UploadedFile
import com.ttaaa.ultimate.app.session.SessionFilter
import com.ttaaa.ultimate.app.session.SessionService
import com.ttaaa.ultimate.domain.GeoPoint
import com.ttaaa.ultimate.domain.RecordingMode
import com.ttaaa.ultimate.domain.Surface
import com.ttaaa.ultimate.domain.SurfaceSource
import com.ttaaa.ultimate.domain.WindowMetrics
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.time.Instant
import java.util.Optional
import java.util.UUID

/** One row of the sessions list (spec 10.1, screen 1). Metrics come from the cached session metrics. */
data class SessionSummary(
    val id: UUID,
    val startTime: Instant,
    val localTzOffsetSec: Int?,
    val fileName: String,
    val geozoneId: UUID?,
    val geozoneName: String?,
    val surface: Surface,
    val surfaceSource: SurfaceSource,
    val recordingMode: RecordingMode,
    val elapsedSec: Int,
    val activeSec: Int,
    val effortCount: Int,
    /** m/s */
    val maxSpeed: Double?,
    /** m/s */
    val movingSpeed: Double?,
    val movingPaceSecPerKm: Double?,
    /** Analysed with an older `ANALYSIS_VERSION`; recompute to update. */
    val outdated: Boolean,
)

/** A session with its laps, segments and metrics (spec 9.1). */
data class SessionDetail(
    val id: UUID,
    val fileName: String,
    val uploadedAt: Instant,
    val startTime: Instant,
    val localTzOffsetSec: Int?,
    val elapsedSec: Int,
    val timerSec: Int,
    /** Distance recorded by the watch, m. */
    val distanceM: Double?,
    val device: String?,
    val sport: String?,
    val subSport: String?,
    val startPosition: GeoPoint?,
    val geozoneId: UUID?,
    val geozoneName: String?,
    val surface: Surface,
    val surfaceSource: SurfaceSource,
    val notes: String?,
    val recordingMode: RecordingMode,
    val analysisVersion: Int,
    val outdated: Boolean,
    val laps: List<LapDto>,
    val segments: List<SegmentDto>,
    val metrics: WindowMetrics,
)

/**
 * Changes to a session. `surface` sets a MANUAL surface. `notes`: absent leaves them unchanged, null or empty clears
 * them.
 */
data class SessionPatch(val surface: Surface? = null, val notes: Optional<String>? = null)

@RestController
@RequestMapping("/api/sessions")
@Tag(name = "Sessions")
class SessionController(
    private val sessionService: SessionService,
    private val uploads: UploadService,
    private val geozones: GeozoneRepository,
    private val views: SessionViews,
) {

    @PostMapping("/upload", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(summary = "Upload .fit files or .zip archives of them; one result per FIT file")
    fun upload(@RequestPart("files") files: List<MultipartFile>): List<UploadResult> =
        uploads.upload(files.map { UploadedFile(it.originalFilename ?: it.name, it.bytes) })

    @GetMapping
    @Operation(summary = "List sessions, newest first; `from` and `to` filter the start time, `to` exclusive")
    fun list(
        @RequestParam(required = false) from: Instant?,
        @RequestParam(required = false) to: Instant?,
        @RequestParam(required = false) surface: Surface?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): Page<SessionSummary> {
        val (sessions, total) = sessionService.list(SessionFilter(from, to, surface), page, size)
        val geozoneNames = geozones.findAll().associate { it.id to it.name }
        return Page(
            items = sessions.map { views.summary(it, geozoneNames[it.geozoneId]) },
            page = page,
            size = size,
            totalItems = total,
            totalPages = ((total + size - 1) / size).toInt(),
        )
    }

    @GetMapping("/{id}")
    fun detail(@PathVariable id: UUID): SessionDetail = views.detail(sessionService.get(id))

    @PatchMapping("/{id}")
    @Operation(summary = "Set the surface (becomes MANUAL) and/or the notes")
    fun update(@PathVariable id: UUID, @RequestBody patch: SessionPatch): SessionDetail =
        views.detail(sessionService.update(id, patch.surface, patch.notes))

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete the session and its raw file")
    fun delete(@PathVariable id: UUID) = sessionService.delete(id)

    @GetMapping("/{id}/file", produces = [MediaType.APPLICATION_OCTET_STREAM_VALUE])
    @Operation(summary = "Download the original FIT file")
    fun file(@PathVariable id: UUID): ResponseEntity<ByteArray> {
        val (session, content) = sessionService.rawFile(id)
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(session.fileName).build().toString())
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(content)
    }
}
