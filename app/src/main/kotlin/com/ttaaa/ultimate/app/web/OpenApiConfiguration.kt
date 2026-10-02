package com.ttaaa.ultimate.app.web

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.servers.Server
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** OpenAPI 3 description at `/v3/api-docs` (spec 7.1); the frontend generates its types from it (spec 10.3). */
@Configuration
class OpenApiConfiguration {

    @Bean
    fun openApi(): OpenAPI = OpenAPI()
        // Same-origin API: a relative server keeps the document independent of host and port.
        .servers(listOf(Server().url("/")))
        .info(
        Info()
            .title("Ultimate Analytics API")
            .version("v1")
            .description(
                "Sprint analytics for Ultimate Frisbee trainings. Times inside a session are integer seconds `t` " +
                    "from its first sample; units are m, s, m/s, m/s² and bpm. Acceleration values are derived " +
                    "from 1 Hz GPS speed.",
            ),
    )

    /**
     * Marks every property that cannot be null as required, so the generated TypeScript types match the Kotlin types:
     * `activeSec: number` rather than `activeSec?: number`. springdoc already maps Kotlin nullability to `null` types.
     */
    @Bean
    fun nonNullPropertiesAreRequired(): OpenApiCustomizer = OpenApiCustomizer { openApi ->
        openApi.components?.schemas?.values?.forEach { schema ->
            val required = schema.properties.orEmpty().filterValues { !it.isNullable() }.keys.sorted()
            schema.required = required.ifEmpty { null }
        }
    }

    private fun Schema<*>.isNullable(): Boolean =
        nullable == true || types?.contains("null") == true ||
            listOfNotNull(oneOf, anyOf).flatten().any { it.types?.contains("null") == true }
}
