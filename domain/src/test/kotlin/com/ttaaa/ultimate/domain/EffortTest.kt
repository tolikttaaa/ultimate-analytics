package com.ttaaa.ultimate.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class EffortTest {

    private val metrics = EffortMetrics(
        startSpeed = 1.0, peakSpeed = 6.0, timeToPeakSec = 3, durationSec = 5, distanceM = 22.0, meanSpeed = 4.4,
        meanAccel = 1.7, peakAccel = 2.4, speedAt1s = 3.0, speedAt2s = 5.0, speedAt3s = 6.0, meanSpeedFirst3s = 4.7,
        distanceFirst3s = 9.0, timeTo80PctPeakSec = 1.8, maxDecelAfter = -2.1, hrStart = 120, hrMax = 150,
    )

    @Test
    fun `start, peak and end are ordered`() {
        Effort(startT = 10, peakT = 13, endT = 15, metrics).range shouldBe TimeRange(10, 15)
        shouldThrow<IllegalArgumentException> { Effort(startT = 14, peakT = 13, endT = 15, metrics) }
        shouldThrow<IllegalArgumentException> { Effort(startT = 10, peakT = 16, endT = 15, metrics) }
        shouldThrow<IllegalArgumentException> { Effort(startT = -1, peakT = 13, endT = 15, metrics) }
    }
}
