package com.ttaaa.ultimate.app.metrics

import com.ttaaa.ultimate.analysis.ANALYSIS_VERSION
import com.ttaaa.ultimate.analysis.analyze
import com.ttaaa.ultimate.app.NotFoundException
import com.ttaaa.ultimate.app.ingestion.SessionImporter
import com.ttaaa.ultimate.app.session.SessionRepository
import com.ttaaa.ultimate.app.storage.RawFileStore
import com.ttaaa.ultimate.domain.AnalysisParameters
import com.ttaaa.ultimate.domain.Session
import com.ttaaa.ultimate.fit.FitParser
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

/** Analyses sessions again from their raw files with the current parameters (spec 8.2). */
@Service
class RecomputeService(
    private val sessions: SessionRepository,
    private val rawFiles: RawFileStore,
    private val importer: SessionImporter,
    private val params: AnalysisParameters,
) {

    fun recompute(id: UUID): Session {
        val session = sessions.findById(id) ?: throw NotFoundException("Session $id not found")
        if (!rawFiles.exists(session.fileSha256)) throw NotFoundException("Raw file of session $id not found")
        val raw = FitParser.parse(rawFiles.read(session.fileSha256).inputStream())
        return importer.replaceAnalysis(session, raw, analyze(raw, params))
    }

    /** Recomputes every session analysed with an older `ANALYSIS_VERSION`; returns how many were recomputed. */
    fun recomputeOutdated(): Int = sessions.findOutdated(ANALYSIS_VERSION).count { session ->
        try {
            recompute(session.id)
            true
        } catch (e: Exception) {
            log.error("Recompute of session {} failed", session.id, e)
            false
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(RecomputeService::class.java)
    }
}
