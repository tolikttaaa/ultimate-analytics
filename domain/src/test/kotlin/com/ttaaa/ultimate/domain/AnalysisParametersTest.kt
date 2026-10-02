package com.ttaaa.ultimate.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AnalysisParametersTest {

    private val defaults = AnalysisParameters()

    @Test
    fun `defaults define one upper bound per speed zone below SPRINT`() {
        defaults.speedZones.size shouldBe SpeedZone.entries.size - 1
    }

    @Test
    fun `rejects inconsistent values`() {
        val invalid = mapOf(
            "negative interpolation gap" to { defaults.copy(maxInterpolationGapSec = -1) },
            "zero every-second share" to { defaults.copy(everySecondRecordingMinShare = 0.0) },
            "every-second share above 1" to { defaults.copy(everySecondRecordingMinShare = 1.1) },
            "negative Smart recording gap" to { defaults.copy(smartRecordingMaxGapSec = -1) },
            "zero plausible speed" to { defaults.copy(maxPlausibleSpeed = 0.0) },
            "even SG window" to { defaults.copy(sgWindow = 4) },
            "SG window below 3" to { defaults.copy(sgWindow = 1, sgOrder = 0) },
            "SG order 0" to { defaults.copy(sgOrder = 0) },
            "SG order not below window" to { defaults.copy(sgOrder = 5) },
            "zero pause duration" to { defaults.copy(minPauseDurationSec = 0) },
            "negative spike tolerance" to { defaults.copy(pauseSpikeToleranceSec = -1) },
            "zero start acceleration" to { defaults.copy(effortStartAccel = 0.0) },
            "end peak ratio of 1" to { defaults.copy(effortEndPeakRatio = 1.0) },
            "min duration above max" to { defaults.copy(effortMinDurationSec = 16) },
            "zero min duration" to { defaults.copy(effortMinDurationSec = 0) },
            "non-negative deceleration threshold" to { defaults.copy(decelThreshold = 0.0) },
            "too few speed zones" to { defaults.copy(speedZones = listOf(1.0, 2.0, 4.0, 5.5)) },
            "decreasing speed zones" to { defaults.copy(speedZones = listOf(1.0, 4.0, 2.0, 5.5, 7.0)) },
            "zero first speed zone" to { defaults.copy(speedZones = listOf(0.0, 2.0, 4.0, 5.5, 7.0)) },
            "zero max heart rate" to { defaults.copy(hrMax = 0) },
        )

        invalid.forEach { (case, build) ->
            withClue(case) { shouldThrow<IllegalArgumentException> { build() } }
        }
    }
}
