package com.ttaaa.ultimate.app.web

import com.ttaaa.ultimate.analysis.ANALYSIS_VERSION
import com.ttaaa.ultimate.app.geozone.GeozoneRepository
import com.ttaaa.ultimate.app.metrics.MetricsService
import com.ttaaa.ultimate.app.segment.SegmentRepository
import com.ttaaa.ultimate.app.session.LapRepository
import com.ttaaa.ultimate.domain.Session
import org.springframework.stereotype.Component

/** Builds the session representations of the API from the stored data. */
@Component
class SessionViews(
    private val metrics: MetricsService,
    private val geozones: GeozoneRepository,
    private val laps: LapRepository,
    private val segments: SegmentRepository,
) {

    fun summary(session: Session, geozoneName: String?): SessionSummary {
        val metrics = metrics.sessionMetrics(session).metrics
        return SessionSummary(
            id = session.id,
            startTime = session.startTime,
            localTzOffsetSec = session.localTzOffsetSec,
            fileName = session.fileName,
            geozoneId = session.geozoneId,
            geozoneName = geozoneName,
            surface = session.surface,
            surfaceSource = session.surfaceSource,
            recordingMode = session.recordingMode,
            elapsedSec = session.elapsedSec,
            activeSec = metrics.time.activeSec,
            effortCount = metrics.efforts.count,
            maxSpeed = metrics.distance.maxSpeed,
            movingSpeed = metrics.distance.movingSpeed,
            movingPaceSecPerKm = metrics.distance.movingPaceSecPerKm,
            outdated = session.analysisVersion < ANALYSIS_VERSION,
        )
    }

    fun detail(session: Session) = SessionDetail(
        id = session.id,
        fileName = session.fileName,
        uploadedAt = session.uploadedAt,
        startTime = session.startTime,
        localTzOffsetSec = session.localTzOffsetSec,
        elapsedSec = session.elapsedSec,
        timerSec = session.timerSec,
        distanceM = session.distanceM,
        device = session.device,
        sport = session.sport,
        subSport = session.subSport,
        startPosition = session.startPosition,
        geozoneId = session.geozoneId,
        geozoneName = session.geozoneId?.let { geozones.findById(it)?.name },
        surface = session.surface,
        surfaceSource = session.surfaceSource,
        notes = session.notes,
        recordingMode = session.recordingMode,
        analysisVersion = session.analysisVersion,
        outdated = session.analysisVersion < ANALYSIS_VERSION,
        laps = laps.findBySession(session.id).map(::LapDto),
        segments = segments.findBySession(session.id).map(::SegmentDto),
        metrics = metrics.sessionMetrics(session).metrics,
    )
}
