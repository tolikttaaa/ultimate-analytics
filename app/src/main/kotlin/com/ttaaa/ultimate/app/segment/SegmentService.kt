package com.ttaaa.ultimate.app.segment

import com.ttaaa.ultimate.app.ConflictException
import com.ttaaa.ultimate.app.NotFoundException
import com.ttaaa.ultimate.app.metrics.MetricsSnapshotRepository
import com.ttaaa.ultimate.app.session.EffortRepository
import com.ttaaa.ultimate.app.session.LapRepository
import com.ttaaa.ultimate.app.session.SessionRepository
import com.ttaaa.ultimate.domain.MetricsScope
import com.ttaaa.ultimate.domain.Segment
import com.ttaaa.ultimate.domain.SegmentSource
import com.ttaaa.ultimate.domain.Session
import com.ttaaa.ultimate.domain.TimeRange
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Optional
import java.util.UUID

/**
 * Editing the segments of a session (spec 9.1, 10.2). Every change runs in one transaction that locks the session, so
 * segments never overlap and stay inside `[0, lastT]` (spec 5, invariants 1-2). Afterwards efforts are attributed to
 * the segment containing their start again, and the cached metrics of segments whose bounds changed are dropped; they
 * are computed again when read. Samples and efforts themselves never change (invariant 5).
 *
 * A segment whose bounds are edited, split or merged becomes MANUAL; changing only its type or label keeps its source.
 */
@Service
class SegmentService(
    private val sessions: SessionRepository,
    private val segments: SegmentRepository,
    private val laps: LapRepository,
    private val efforts: EffortRepository,
    private val snapshots: MetricsSnapshotRepository,
) {

    /**
     * Creates a segment. With [overwrite] it takes its time from the segments it overlaps, which are trimmed, split
     * around it or removed; parts left shorter than [Segment.MIN_DURATION_SEC] go too. Without it, an overlap is a
     * conflict (spec 9.1).
     */
    @Transactional
    fun create(
        sessionId: UUID,
        range: TimeRange,
        drillTypeId: UUID?,
        label: String?,
        overwrite: Boolean = false,
    ): Segment {
        val session = lockSession(sessionId)
        val segment = Segment(UUID.randomUUID(), sessionId, range, checkDrillType(drillTypeId), label.normalized(), SegmentSource.MANUAL)
        val existing = segments.findBySession(sessionId)
        val others = if (overwrite) makeRoom(range, existing) else existing
        checkPlacement(session, segment, others)
        segments.insertAll(listOf(segment))
        reattributeEfforts(sessionId)
        return segment
    }

    /** [drillTypeId] and [label]: null leaves them, an empty optional clears them. */
    @Transactional
    fun update(
        sessionId: UUID,
        segmentId: UUID,
        startT: Int?,
        endT: Int?,
        drillTypeId: Optional<UUID>?,
        label: Optional<String>?,
    ): Segment {
        val session = lockSession(sessionId)
        val all = segments.findBySession(sessionId)
        val current = all.find(segmentId)
        val range = TimeRange(startT ?: current.range.fromT, endT ?: current.range.toT)
        val boundsChanged = range != current.range
        val updated = current.copy(
            range = range,
            drillTypeId = if (drillTypeId != null) checkDrillType(drillTypeId.orElse(null)) else current.drillTypeId,
            label = if (label != null) label.orElse(null).normalized() else current.label,
            source = if (boundsChanged) SegmentSource.MANUAL else current.source,
        )
        checkPlacement(session, updated, all - current)
        segments.update(updated)
        if (boundsChanged) {
            snapshots.delete(MetricsScope.SEGMENT, segmentId)
            reattributeEfforts(sessionId)
        }
        return updated
    }

    @Transactional
    fun delete(sessionId: UUID, segmentId: UUID) {
        lockSession(sessionId)
        segments.findBySession(sessionId).find(segmentId)
        remove(segmentId)
    }

    /** Splits a segment at [atT]; both parts keep its type and label (spec 9.1). */
    @Transactional
    fun split(sessionId: UUID, segmentId: UUID, atT: Int): List<Segment> {
        lockSession(sessionId)
        val current = segments.findBySession(sessionId).find(segmentId)
        require(atT > current.range.fromT && atT < current.range.toT) { "atT must lie inside the segment" }
        val left = current.copy(range = TimeRange(current.range.fromT, atT), source = SegmentSource.MANUAL)
        val right = current.copy(id = UUID.randomUUID(), range = TimeRange(atT, current.range.toT), source = SegmentSource.MANUAL)
        segments.update(left)
        segments.insertAll(listOf(right))
        snapshots.delete(MetricsScope.SEGMENT, segmentId)
        reattributeEfforts(sessionId)
        return listOf(left, right)
    }

    /**
     * Merges consecutive segments (no other segment between them) into the first one, which keeps its type and label
     * (spec 9.1); the gaps between them become part of the merged segment.
     */
    @Transactional
    fun merge(sessionId: UUID, segmentIds: List<UUID>): Segment {
        lockSession(sessionId)
        require(segmentIds.distinct().size >= 2) { "Merging needs at least two segments" }
        val all = segments.findBySession(sessionId)
        val merging = segmentIds.distinct().map { all.find(it) }.sortedBy { it.range.fromT }
        val span = TimeRange(merging.first().range.fromT, merging.last().range.toT)
        val inSpan = all.filter { it.range.overlaps(span) }
        require(inSpan.map { it.id }.toSet() == merging.map { it.id }.toSet()) {
            "Only consecutive segments can be merged: another segment lies between them"
        }
        val merged = merging.first().copy(range = span, source = SegmentSource.MANUAL)
        segments.update(merged)
        merging.drop(1).forEach { remove(it.id) }
        snapshots.delete(MetricsScope.SEGMENT, merged.id)
        reattributeEfforts(sessionId)
        return merged
    }

    /** Replaces all segments with the lap-based ones (spec 9.1); [confirm] guards against accidental resets. */
    @Transactional
    fun resetFromLaps(sessionId: UUID, confirm: Boolean): List<Segment> {
        require(confirm) { "Resetting replaces all segments; repeat the request with confirm=true" }
        lockSession(sessionId)
        segments.findBySession(sessionId).forEach { remove(it.id) }
        val lapSegments = segmentsFromLaps(sessionId, laps.findBySession(sessionId))
        segments.insertAll(lapSegments)
        reattributeEfforts(sessionId)
        return lapSegments
    }

    fun get(sessionId: UUID, segmentId: UUID): Segment = segments.findBySession(sessionId).find(segmentId)

    /** Cuts [range] out of the segments it overlaps; returns the segments afterwards. */
    private fun makeRoom(range: TimeRange, all: List<Segment>): List<Segment> = all.flatMap { segment ->
        if (!segment.range.overlaps(range)) return@flatMap listOf(segment)
        // What is left before and after the new segment; a part may be empty, so bounds come before ranges.
        val parts = listOf(
            segment.range.fromT to minOf(range.fromT, segment.range.toT),
            maxOf(range.toT, segment.range.fromT) to segment.range.toT,
        ).filter { (from, to) -> to - from >= Segment.MIN_DURATION_SEC }.map { (from, to) -> TimeRange(from, to) }
        if (parts.isEmpty()) {
            remove(segment.id)
            return@flatMap emptyList()
        }
        // The first part keeps the segment's id; a second part (the window was inside it) is a new segment.
        val kept = segment.copy(range = parts.first(), source = SegmentSource.MANUAL)
        val split = parts.drop(1).map { kept.copy(id = UUID.randomUUID(), range = it) }
        snapshots.delete(MetricsScope.SEGMENT, segment.id)
        segments.update(kept)
        segments.insertAll(split)
        listOf(kept) + split
    }

    private fun remove(segmentId: UUID) {
        // Efforts lose the segment through the foreign key; the snapshot has no foreign key and is removed here.
        segments.delete(segmentId)
        snapshots.delete(MetricsScope.SEGMENT, segmentId)
    }

    private fun lockSession(sessionId: UUID): Session {
        if (!sessions.lock(sessionId)) throw NotFoundException("Session $sessionId not found")
        return checkNotNull(sessions.findById(sessionId))
    }

    private fun List<Segment>.find(segmentId: UUID): Segment =
        firstOrNull { it.id == segmentId } ?: throw NotFoundException("Segment $segmentId not found in this session")

    private fun checkPlacement(session: Session, segment: Segment, others: List<Segment>) {
        require(segment.range.toT <= session.elapsedSec) {
            "A segment must end within the session (at most ${session.elapsedSec})"
        }
        others.firstOrNull { it.range.overlaps(segment.range) }?.let {
            throw ConflictException("The segment overlaps segment ${it.id} [${it.range.fromT}, ${it.range.toT}]")
        }
    }

    private fun checkDrillType(drillTypeId: UUID?): UUID? {
        if (drillTypeId != null) require(segments.drillTypeExists(drillTypeId)) { "Drill type $drillTypeId does not exist" }
        return drillTypeId
    }

    private fun reattributeEfforts(sessionId: UUID) {
        val current = segments.findBySession(sessionId)
        efforts.findBySession(sessionId).forEach { stored ->
            val segmentId = segmentOf(stored.effort, current)?.id
            if (segmentId != stored.segmentId) efforts.updateSegment(stored.id, segmentId)
        }
    }

    private fun String?.normalized(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}
