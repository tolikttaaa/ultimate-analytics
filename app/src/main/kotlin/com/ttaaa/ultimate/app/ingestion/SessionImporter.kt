package com.ttaaa.ultimate.app.ingestion

import com.ttaaa.ultimate.analysis.ANALYSIS_VERSION
import com.ttaaa.ultimate.analysis.AnalysisResult
import com.ttaaa.ultimate.analysis.geo.applyGeozoneMatch
import com.ttaaa.ultimate.analysis.geo.matchGeozone
import com.ttaaa.ultimate.app.geozone.GeozoneRepository
import com.ttaaa.ultimate.app.metrics.MetricsSnapshotRepository
import com.ttaaa.ultimate.app.metrics.metricsSnapshots
import com.ttaaa.ultimate.app.segment.SegmentRepository
import com.ttaaa.ultimate.app.segment.segmentOf
import com.ttaaa.ultimate.app.segment.segmentsFromLaps
import com.ttaaa.ultimate.app.session.EffortRepository
import com.ttaaa.ultimate.app.session.LapRepository
import com.ttaaa.ultimate.app.session.SampleRepository
import com.ttaaa.ultimate.app.session.SessionRepository
import com.ttaaa.ultimate.app.session.StoredEffort
import com.ttaaa.ultimate.app.session.asStored
import com.ttaaa.ultimate.domain.AnalysisParameters
import com.ttaaa.ultimate.domain.RawSession
import com.ttaaa.ultimate.domain.Session
import com.ttaaa.ultimate.domain.Surface
import com.ttaaa.ultimate.domain.SurfaceSource
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID
import kotlin.math.roundToInt

/** Writes an analysed session and everything derived from it in one transaction (spec 7.4 step 5). */
@Component
class SessionImporter(
    private val sessions: SessionRepository,
    private val samples: SampleRepository,
    private val laps: LapRepository,
    private val segments: SegmentRepository,
    private val efforts: EffortRepository,
    private val snapshots: MetricsSnapshotRepository,
    private val geozones: GeozoneRepository,
    private val params: AnalysisParameters,
    private val clock: Clock,
) {

    @Transactional
    fun import(fileSha256: String, fileName: String, raw: RawSession, analysis: AnalysisResult): Session {
        val unmatched = Session(
            id = UUID.randomUUID(),
            fileSha256 = fileSha256,
            fileName = fileName,
            uploadedAt = clock.instant(),
            startTime = analysis.startTime,
            localTzOffsetSec = raw.localTzOffsetSec,
            elapsedSec = analysis.lastT,
            timerSec = raw.totalTimerSec.roundToInt(),
            distanceM = raw.totalDistanceM,
            device = raw.device,
            sport = raw.sport,
            subSport = raw.subSport,
            startPosition = analysis.referencePosition,
            geozoneId = null,
            surface = Surface.UNKNOWN,
            surfaceSource = SurfaceSource.NONE,
            notes = null,
            analysisVersion = ANALYSIS_VERSION,
            recordingMode = analysis.recordingMode,
        )
        val session = applyGeozoneMatch(unmatched, matchGeozone(analysis.referencePosition, geozones.findAll()))
        val storedSamples = analysis.samples.map { it.asStored() }
        val lapSegments = segmentsFromLaps(session.id, analysis.laps)

        sessions.insert(session)
        samples.insertAll(session.id, storedSamples)
        laps.insertAll(session.id, analysis.laps)
        segments.insertAll(lapSegments)
        efforts.insertAll(
            analysis.efforts.map { StoredEffort(UUID.randomUUID(), session.id, segmentOf(it, lapSegments)?.id, it) },
        )
        metricsSnapshots(session.id, analysis.lastT, storedSamples, analysis.efforts, lapSegments, params)
            .forEach(snapshots::save)
        return session
    }
}
