package com.ttaaa.ultimate.app.web

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** OpenAPI 3 description at `/v3/api-docs` (spec 7.1); the frontend generates its types from it (spec 10.3). */
@Configuration
class OpenApiConfiguration {

    @Bean
    fun openApi(): OpenAPI = OpenAPI().info(
        Info()
            .title("Ultimate Analytics API")
            .version("v1")
            .description(
                "Sprint analytics for Ultimate Frisbee trainings. Times inside a session are integer seconds `t` " +
                    "from its first sample; units are m, s, m/s, m/s² and bpm. Acceleration values are derived " +
                    "from 1 Hz GPS speed.",
            ),
    )
}
