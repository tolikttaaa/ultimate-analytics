package com.ttaaa.ultimate.app.drilltype

import com.ttaaa.ultimate.analysis.metrics.aggregateMetrics
import com.ttaaa.ultimate.app.ConflictException
import com.ttaaa.ultimate.app.NotFoundException
import com.ttaaa.ultimate.app.metrics.MetricsService
import com.ttaaa.ultimate.app.segment.SegmentRepository
import com.ttaaa.ultimate.app.session.SessionFilter
import com.ttaaa.ultimate.app.session.SessionRepository
import com.ttaaa.ultimate.domain.DrillKind
import com.ttaaa.ultimate.domain.DrillType
import com.ttaaa.ultimate.domain.Session
import com.ttaaa.ultimate.domain.WindowMetrics
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import java.util.UUID

/** Metrics of one drill type in one session: all its segments of that type taken together. */
data class DrillTypeSessionStats(val session: Session, val segmentCount: Int, val metrics: WindowMetrics)

data class DrillTypeStats(val drillType: DrillType, val totals: WindowMetrics, val sessions: List<DrillTypeSessionStats>)

@Service
class DrillTypeService(
    private val drillTypes: DrillTypeRepository,
    private val segments: SegmentRepository,
    private val sessions: SessionRepository,
    private val metrics: MetricsService,
) {

    fun list(): List<DrillType> = drillTypes.findAll()

    fun get(id: UUID): DrillType = drillTypes.findById(id) ?: throw NotFoundException("Drill type $id not found")

    fun create(code: String, name: String, color: String, kind: DrillKind): DrillType {
        val drillType = DrillType(UUID.randomUUID(), code.normalizedCode(), name.trim(), color, kind)
        uniqueCode(drillType.code) { drillTypes.insert(drillType) }
        return drillType
    }

    fun update(id: UUID, code: String?, name: String?, color: String?, kind: DrillKind?): DrillType {
        val current = get(id)
        val updated = current.copy(
            code = code?.normalizedCode() ?: current.code,
            name = name?.trim() ?: current.name,
            color = color ?: current.color,
            kind = kind ?: current.kind,
        )
        uniqueCode(updated.code) { drillTypes.update(updated) }
        return updated
    }

    /** Deletes the drill type; its segments stay, without a type (spec 9.1). */
    fun delete(id: UUID) {
        if (!drillTypes.delete(id)) throw NotFoundException("Drill type $id not found")
    }

    /**
     * Metrics of all segments of the drill type in the sessions matching [filter] (spec 6.5): totals across sessions
     * and one row per session in time order, for the trend chart.
     */
    fun stats(id: UUID, filter: SessionFilter): DrillTypeStats {
        val drillType = get(id)
        val bySession = segments.findByDrillType(id, filter).groupBy { it.sessionId }
        val rows = bySession.map { (sessionId, sessionSegments) ->
            val session = checkNotNull(sessions.findById(sessionId))
            val segmentMetrics = sessionSegments.map { metrics.segmentMetrics(session, it).metrics }
            DrillTypeSessionStats(session, sessionSegments.size, aggregateMetrics(segmentMetrics))
        }.sortedBy { it.session.startTime }
        return DrillTypeStats(drillType, aggregateMetrics(rows.map { it.metrics }), rows)
    }

    private fun uniqueCode(code: String, write: () -> Unit) {
        try {
            write()
        } catch (_: DuplicateKeyException) {
            throw ConflictException("A drill type with code $code already exists")
        }
    }

    private fun String.normalizedCode() = trim().uppercase()
}
