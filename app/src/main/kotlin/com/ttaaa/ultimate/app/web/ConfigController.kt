package com.ttaaa.ultimate.app.web

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * `app.map.*`: the base maps of the frontend (spec 10.2). Tile URLs can carry a provider key, so they are set per
 * environment (env vars, Helm values) and never committed; the provider's terms apply (`docs/DECISIONS.md`).
 */
@ConfigurationProperties("app.map")
data class MapProperties(
    /** MapLibre style of the default vector base map. */
    val vectorStyleUrl: String = "https://tiles.openfreemap.org/styles/liberty",
    /** The vector base map in the dark theme. */
    val vectorStyleUrlDark: String = "https://tiles.openfreemap.org/styles/dark",
    val satellite: Satellite = Satellite(),
) {
    data class Satellite(
        /** XYZ raster tile URL with `{z}`, `{x}` and `{y}`; empty leaves the satellite base map out. */
        val tilesUrl: String = "",
        /** Shown on the map, as the provider's terms require. */
        val attribution: String = "",
        val maxZoom: Int = 19,
        val tileSize: Int = 256,
    ) {
        init {
            require(tilesUrl.isBlank() || listOf("{z}", "{x}", "{y}").all { it in tilesUrl }) {
                "app.map.satellite.tiles-url needs {z}, {x} and {y}"
            }
        }
    }

    fun toConfig() = MapConfig(
        vectorStyleUrl = vectorStyleUrl,
        vectorStyleUrlDark = vectorStyleUrlDark,
        satellite = satellite.takeIf { it.tilesUrl.isNotBlank() }?.let {
            SatelliteConfig(it.tilesUrl, it.attribution, it.maxZoom, it.tileSize)
        },
    )
}

/** Settings the frontend reads at runtime, so one image serves every environment (spec 11). */
data class UiConfig(val map: MapConfig)

data class MapConfig(
    val vectorStyleUrl: String,
    val vectorStyleUrlDark: String,
    /** Null when no satellite tiles are configured. */
    val satellite: SatelliteConfig?,
)

data class SatelliteConfig(val tilesUrl: String, val attribution: String, val maxZoom: Int, val tileSize: Int)

@RestController
@Tag(name = "Config")
class ConfigController(private val map: MapProperties) {

    @GetMapping("/api/config")
    @Operation(summary = "Frontend settings: the base maps and their tile sources")
    fun config(): UiConfig = UiConfig(map.toConfig())
}
