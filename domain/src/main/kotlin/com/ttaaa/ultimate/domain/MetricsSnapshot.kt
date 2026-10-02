package com.ttaaa.ultimate.domain

import java.util.UUID

enum class MetricsScope { SESSION, SEGMENT }

/**
 * Cached [WindowMetrics] of a whole session or of one segment (spec 5, 8.2). Metrics of arbitrary windows are
 * computed on request and never stored.
 */
data class MetricsSnapshot(
    val sessionId: UUID,
    val scope: MetricsScope,
    /** The session id for [MetricsScope.SESSION], the segment id for [MetricsScope.SEGMENT]. */
    val scopeId: UUID,
    val analysisVersion: Int,
    val metrics: WindowMetrics,
) {
    init {
        require(scope != MetricsScope.SESSION || scopeId == sessionId) {
            "A session snapshot is scoped to its own session"
        }
    }
}
