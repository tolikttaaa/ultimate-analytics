package com.ttaaa.ultimate.app.metrics

import com.ttaaa.ultimate.analysis.metrics.windowMetrics
import com.ttaaa.ultimate.domain.AnalysisParameters
import com.ttaaa.ultimate.domain.Effort
import com.ttaaa.ultimate.domain.MetricsScope
import com.ttaaa.ultimate.domain.MetricsSnapshot
import com.ttaaa.ultimate.domain.Sample
import com.ttaaa.ultimate.domain.Segment
import com.ttaaa.ultimate.domain.TimeRange
import java.util.UUID

/**
 * The metrics to cache for a session: the whole session `[0, lastT]` and every segment (spec 6.1 step 11).
 * [samples] must have the stored precision, so that the cache equals metrics computed later from the database;
 * [analysisVersion] is the version they were analysed with.
 */
fun metricsSnapshots(
    sessionId: UUID,
    analysisVersion: Int,
    lastT: Int,
    samples: List<Sample>,
    efforts: List<Effort>,
    segments: List<Segment>,
    params: AnalysisParameters,
): List<MetricsSnapshot> {
    val session = MetricsSnapshot(
        sessionId, MetricsScope.SESSION, sessionId, analysisVersion,
        windowMetrics(samples, efforts, TimeRange(0, lastT), params),
    )
    return listOf(session) + segments.map { segment ->
        MetricsSnapshot(
            sessionId, MetricsScope.SEGMENT, segment.id, analysisVersion,
            windowMetrics(samples, efforts, segment.range, params),
        )
    }
}
