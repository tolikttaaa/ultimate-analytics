package com.ttaaa.ultimate.app.web

import com.ttaaa.ultimate.app.IntegrationTest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class ConfigApiTest : IntegrationTest() {

    @Test
    fun `serves the OpenFreeMap vector base map and no satellite by default`() {
        val config = get("/api/config")

        config.statusCode shouldBe HttpStatus.OK
        config.json["map"]["vectorStyleUrl"].asString() shouldBe "https://tiles.openfreemap.org/styles/liberty"
        config.json["map"]["satellite"].isNull shouldBe true
    }
}

class MapPropertiesTest {

    @Test
    fun `configured satellite tiles reach the frontend`() {
        val properties = MapProperties(
            satellite = MapProperties.Satellite(
                tilesUrl = "https://tiles.example.com/{z}/{x}/{y}.jpg?key=abc",
                attribution = "© Example",
                maxZoom = 20,
            ),
        )

        properties.toConfig().satellite shouldBe
            SatelliteConfig("https://tiles.example.com/{z}/{x}/{y}.jpg?key=abc", "© Example", 20, 256)
    }

    @Test
    fun `a blank tile URL leaves the satellite base map out`() {
        MapProperties(satellite = MapProperties.Satellite(tilesUrl = " ")).toConfig().satellite shouldBe null
    }

    @Test
    fun `a tile URL without placeholders is rejected at startup`() {
        val error = shouldThrow<IllegalArgumentException> {
            MapProperties.Satellite(tilesUrl = "https://tiles.example.com/tile.jpg")
        }
        error.message!! shouldContain "{z}, {x} and {y}"
    }
}
