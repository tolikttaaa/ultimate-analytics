package com.ttaaa.ultimate.domain

import io.kotest.assertions.throwables.shouldThrow
import org.junit.jupiter.api.Test
import java.util.UUID

class MetricsSnapshotTest {

    private val metrics = WindowMetrics(
        range = TimeRange(0, 600),
        time = TimeMetrics(elapsedSec = 600, activeSec = 600, pausedSec = 0, gapSec = 0, workRestRatio = null),
        distance = DistanceMetrics(
            distanceM = 0.0, activeDistanceM = 0.0, movingSpeed = null, movingPaceSecPerKm = null, maxSpeed = null,
        ),
        zones = SpeedZone.entries.map { SpeedZoneMetrics(it, timeSec = 0, distanceM = 0.0) },
        efforts = EffortStats(
            count = 0, perActiveMin = 0.0, peakSpeed = null, meanSpeed = null, meanAccel = null, peakAccel = null,
            meanSpeedFirst3s = null, timeTo80PctPeakSec = null,
        ),
        decelCount = 0,
        fatigue = FatigueMetrics(peakSpeedDropPct = null),
        heartRate = HeartRateMetrics(avg = null, max = null, zonesPct = emptyList()),
    )

    @Test
    fun `a session snapshot is scoped to its own session`() {
        val sessionId = UUID.randomUUID()
        MetricsSnapshot(sessionId, MetricsScope.SESSION, scopeId = sessionId, analysisVersion = 1, metrics)
        MetricsSnapshot(sessionId, MetricsScope.SEGMENT, scopeId = UUID.randomUUID(), analysisVersion = 1, metrics)
        shouldThrow<IllegalArgumentException> {
            MetricsSnapshot(sessionId, MetricsScope.SESSION, scopeId = UUID.randomUUID(), analysisVersion = 1, metrics)
        }
    }
}
