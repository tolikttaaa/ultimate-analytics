package com.ttaaa.ultimate.app.geozone

import com.ttaaa.ultimate.app.IntegrationTest
import com.ttaaa.ultimate.domain.GeoPoint
import com.ttaaa.ultimate.domain.Geozone
import com.ttaaa.ultimate.domain.GeozoneShape
import com.ttaaa.ultimate.domain.Surface
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID

class GeozoneRepositoryTest : IntegrationTest() {

    @Autowired private lateinit var geozones: GeozoneRepository

    @Test
    fun `stores circles and polygons`() {
        val circle = Geozone(UUID.randomUUID(), "Field", Surface.GRASS, GeozoneShape.Circle(GeoPoint(34.68, 33.04), 120.0))
        val polygon = Geozone(
            UUID.randomUUID(), "Beach", Surface.SAND,
            GeozoneShape.Polygon(listOf(GeoPoint(34.70, 33.10), GeoPoint(34.70, 33.11), GeoPoint(34.71, 33.11))),
        )

        geozones.insert(circle)
        geozones.insert(polygon)

        geozones.findAll() shouldContainExactlyInAnyOrder listOf(circle, polygon)
    }

    @Test
    fun `polygons are stored as closed GeoJSON rings of lon, lat`() {
        val ring = listOf(GeoPoint(34.70, 33.10), GeoPoint(34.70, 33.11), GeoPoint(34.71, 33.11))
        geozones.insert(Geozone(UUID.randomUUID(), "Beach", Surface.SAND, GeozoneShape.Polygon(ring)))

        val (type, coordinates) = jdbc.sql("select shape->>'type' as type, shape->>'coordinates' as coordinates from geozone")
            .query { rs, _ -> rs.getString("type") to rs.getString("coordinates") }
            .single()

        type shouldBe "Polygon"
        coordinates shouldBe "[[[33.1, 34.7], [33.11, 34.7], [33.11, 34.71], [33.1, 34.7]]]"
    }
}
