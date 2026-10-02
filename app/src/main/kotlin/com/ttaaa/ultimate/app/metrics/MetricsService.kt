package com.ttaaa.ultimate.app.metrics

import com.ttaaa.ultimate.analysis.metrics.windowMetrics
import com.ttaaa.ultimate.app.session.EffortRepository
import com.ttaaa.ultimate.app.session.SampleRepository
import com.ttaaa.ultimate.domain.AnalysisParameters
import com.ttaaa.ultimate.domain.MetricsScope
import com.ttaaa.ultimate.domain.MetricsSnapshot
import com.ttaaa.ultimate.domain.Segment
import com.ttaaa.ultimate.domain.Session
import com.ttaaa.ultimate.domain.TimeRange
import com.ttaaa.ultimate.domain.WindowMetrics
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Metrics from the stored analysis (spec 6.5, 8.2). Session and segment metrics are cached as snapshots and computed
 * again when missing (after segment edits); window metrics are always computed live and never stored.
 */
@Service
class MetricsService(
    private val snapshots: MetricsSnapshotRepository,
    private val samples: SampleRepository,
    private val efforts: EffortRepository,
    private val params: AnalysisParameters,
) {

    fun sessionMetrics(session: Session): MetricsSnapshot =
        snapshots.find(MetricsScope.SESSION, session.id)
            ?: cache(session, MetricsScope.SESSION, session.id, TimeRange(0, session.elapsedSec))

    fun segmentMetrics(session: Session, segment: Segment): MetricsSnapshot =
        snapshots.find(MetricsScope.SEGMENT, segment.id)
            ?: cache(session, MetricsScope.SEGMENT, segment.id, segment.range)

    fun windowMetrics(session: Session, range: TimeRange): WindowMetrics = windowMetrics(
        samples.findBySession(session.id, session.startTime),
        efforts.findBySession(session.id).map { it.effort },
        range,
        params,
    )

    private fun cache(session: Session, scope: MetricsScope, scopeId: UUID, range: TimeRange): MetricsSnapshot {
        // Labelled with the version the stored samples come from, so an outdated session stays visibly outdated.
        val snapshot = MetricsSnapshot(session.id, scope, scopeId, session.analysisVersion, windowMetrics(session, range))
        snapshots.save(snapshot)
        return snapshot
    }
}
