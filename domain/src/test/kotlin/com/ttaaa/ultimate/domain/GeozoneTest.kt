package com.ttaaa.ultimate.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.numericDouble
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.util.UUID

class GeozoneTest {

    private val a = GeoPoint(34.680, 33.040)
    private val b = GeoPoint(34.681, 33.040)
    private val c = GeoPoint(34.681, 33.041)

    @Test
    fun `geo points accept every valid coordinate`() = runTest {
        checkAll(Arb.numericDouble(-90.0, 90.0), Arb.numericDouble(-180.0, 180.0)) { lat, lon ->
            GeoPoint(lat, lon).lat shouldBe lat
        }
    }

    @Test
    fun `geo points reject coordinates out of range`() {
        shouldThrow<IllegalArgumentException> { GeoPoint(90.1, 0.0) }
        shouldThrow<IllegalArgumentException> { GeoPoint(0.0, -180.1) }
        shouldThrow<IllegalArgumentException> { GeoPoint(Double.NaN, 0.0) }
    }

    @Test
    fun `circles need a positive finite radius`() {
        GeozoneShape.Circle(a, radiusM = 80.0)
        shouldThrow<IllegalArgumentException> { GeozoneShape.Circle(a, radiusM = 0.0) }
        shouldThrow<IllegalArgumentException> { GeozoneShape.Circle(a, radiusM = Double.NaN) }
        shouldThrow<IllegalArgumentException> { GeozoneShape.Circle(a, radiusM = Double.POSITIVE_INFINITY) }
    }

    @Test
    fun `polygons need three vertices listed once`() {
        GeozoneShape.Polygon(listOf(a, b, c))
        shouldThrow<IllegalArgumentException> { GeozoneShape.Polygon(listOf(a, b)) }
        shouldThrow<IllegalArgumentException> { GeozoneShape.Polygon(listOf(a, b, c, a)) }
    }

    @Test
    fun `geozones are grass or sand`() {
        val shape = GeozoneShape.Circle(a, radiusM = 80.0)
        Geozone(UUID.randomUUID(), "Beach courts", Surface.SAND, shape)
        shouldThrow<IllegalArgumentException> { Geozone(UUID.randomUUID(), "Somewhere", Surface.UNKNOWN, shape) }
        shouldThrow<IllegalArgumentException> { Geozone(UUID.randomUUID(), "", Surface.GRASS, shape) }
    }
}
