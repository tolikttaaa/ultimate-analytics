package com.ttaaa.ultimate.app

import com.ttaaa.ultimate.domain.AnalysisParameters
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/** `app.analysis.*` (spec 11). */
@ConfigurationProperties("app.analysis")
data class AnalysisProperties(
    /** Maximum heart rate for the HR zones, bpm. */
    val hrMax: Int = 190,
)

@Configuration
class AnalysisConfiguration {

    /** The current analysis parameters: the defaults of `ANALYSIS_VERSION` with the user's settings. */
    @Bean
    fun analysisParameters(properties: AnalysisProperties) = AnalysisParameters(hrMax = properties.hrMax)

    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
