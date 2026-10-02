package com.ttaaa.ultimate.analysis

import com.ttaaa.ultimate.domain.AnalysisParameters
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AnalysisVersionTest {

    /**
     * The `AnalysisParameters` defaults of every `ANALYSIS_VERSION`. When a default changes, bump `ANALYSIS_VERSION`
     * and add the new defaults here; the test fails if only one of the two happens.
     */
    private val defaultsByVersion = mapOf(
        1 to AnalysisParameters(
            maxInterpolationGapSec = 3,
            everySecondRecordingMinShare = 0.9,
            smartRecordingMaxGapSec = 7,
            maxPlausibleSpeed = 11.0,
            sgWindow = 5,
            sgOrder = 2,
            walkSpeedThreshold = 2.0,
            pauseSpeedThreshold = 1.5,
            minPauseDurationSec = 20,
            pauseSpikeToleranceSec = 2,
            effortStartAccel = 1.0,
            effortMinPeakSpeed = 4.5,
            effortMinSpeedGain = 2.0,
            effortEndPeakRatio = 0.7,
            effortEndMinSpeed = 2.5,
            effortMinDurationSec = 2,
            effortMaxDurationSec = 15,
            decelThreshold = -1.5,
            speedZones = listOf(1.0, 2.0, 4.0, 5.5, 7.0),
            hrMax = 190,
        ),
    )

    @Test
    fun `current defaults are recorded for the current ANALYSIS_VERSION`() {
        AnalysisParameters() shouldBe defaultsByVersion[ANALYSIS_VERSION]
    }
}
