package com.ttaaa.ultimate.analysis.geo

import com.ttaaa.ultimate.domain.GeoPoint
import com.ttaaa.ultimate.domain.GeozoneShape
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.numericDouble
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.cos

/** Geometry helpers: points offset by metres from a reference point. */
internal object Places {
    val FIELD = GeoPoint(34.6800, 33.0400)
    private const val METRES_PER_DEGREE = EARTH_RADIUS_M * PI / 180

    fun GeoPoint.offset(northM: Double, eastM: Double = 0.0) = GeoPoint(
        lat + northM / METRES_PER_DEGREE,
        lon + eastM / (METRES_PER_DEGREE * cos(Math.toRadians(lat))),
    )
}

class GeoTest {

    private val points = Arb.bind(Arb.numericDouble(-80.0, 80.0), Arb.numericDouble(-179.0, 179.0), ::GeoPoint)

    @Test
    fun `haversine matches reference distances`() {
        haversineM(GeoPoint(0.0, 0.0), GeoPoint(1.0, 0.0)) shouldBe (111_195.08 plusOrMinus 0.01)
        haversineM(GeoPoint(48.8566, 2.3522), GeoPoint(51.5074, -0.1278)) shouldBe (343_500.0 plusOrMinus 1_000.0)
        with(Places) { haversineM(FIELD, FIELD.offset(northM = 100.0)) shouldBe (100.0 plusOrMinus 1e-6) }
    }

    @Test
    fun `haversine is a distance`() = runTest {
        checkAll(points, points, points) { a, b, c ->
            haversineM(a, a) shouldBe 0.0
            haversineM(a, b) shouldBe (haversineM(b, a) plusOrMinus 1e-6)
            haversineM(a, b) shouldBeGreaterThanOrEqual 0.0
            haversineM(a, c) shouldBeLessThanOrEqual haversineM(a, b) + haversineM(b, c) + 1e-6
        }
    }

    @Test
    fun `a circle contains the points within its radius`() = runTest {
        checkAll(Arb.numericDouble(1.0, 2_000.0), Arb.numericDouble(0.0, 4_000.0), Arb.numericDouble(0.0, 1.0)) { radius, distance, direction ->
            val circle = GeozoneShape.Circle(Places.FIELD, radius)
            val point = with(Places) { Places.FIELD.offset(northM = distance * direction, eastM = distance * (1 - direction)) }
            val actual = haversineM(Places.FIELD, point)

            if (actual < radius - 0.01) circle.contains(point) shouldBe true
            if (actual > radius + 0.01) circle.contains(point) shouldBe false
        }
    }

    @Test
    fun `a concave polygon leaves out its notch`(): Unit = with(Places) {
        // An L-shaped field, 100 m x 100 m without the north-east 60 m x 60 m.
        val corners = listOf(0.0 to 0.0, 0.0 to 100.0, 40.0 to 100.0, 40.0 to 40.0, 100.0 to 40.0, 100.0 to 0.0)
        val field = GeozoneShape.Polygon(corners.map { (north, east) -> FIELD.offset(north, east) })

        field.contains(FIELD.offset(20.0, 20.0)) shouldBe true
        field.contains(FIELD.offset(20.0, 70.0)) shouldBe true
        field.contains(FIELD.offset(70.0, 20.0)) shouldBe true
        field.contains(FIELD.offset(70.0, 70.0)) shouldBe false
        field.contains(FIELD.offset(-5.0, 20.0)) shouldBe false
        field.areaM2() shouldBe (6_400.0 plusOrMinus 6.4)
    }

    @Test
    fun `areas of circles and polygons`(): Unit = with(Places) {
        GeozoneShape.Circle(FIELD, 50.0).areaM2() shouldBe (PI * 2_500 plusOrMinus 1e-9)
        val square = listOf(0.0 to 0.0, 0.0 to 100.0, 100.0 to 100.0, 100.0 to 0.0).map { (n, e) -> FIELD.offset(n, e) }
        GeozoneShape.Polygon(square).areaM2() shouldBe (10_000.0 plusOrMinus 10.0)
        GeozoneShape.Polygon(square.reversed()).areaM2() shouldBe (GeozoneShape.Polygon(square).areaM2() plusOrMinus 1e-6)
        GeozoneShape.Polygon(square.drop(1) + square.first()).areaM2() shouldBe
            (GeozoneShape.Polygon(square).areaM2() plusOrMinus 1e-6)
    }

    @Test
    fun `point in polygon does not depend on the vertex order or direction`() = runTest {
        val offsets = Arb.bind(Arb.numericDouble(-200.0, 200.0), Arb.numericDouble(-200.0, 200.0)) { n, e -> n to e }

        checkAll(Arb.list(offsets, 3..8), offsets, Arb.int(0..7)) { corners, (north, east), shift ->
            val ring = with(Places) { corners.map { (n, e) -> FIELD.offset(n, e) } }
            // A repeated corner is no valid polygon, and a rotation could put it at both ends of the ring.
            if (ring.toSet().size < ring.size) return@checkAll
            val point = with(Places) { FIELD.offset(north, east) }
            val rotated = ring.drop(shift % ring.size) + ring.take(shift % ring.size)

            GeozoneShape.Polygon(rotated).contains(point) shouldBe GeozoneShape.Polygon(ring).contains(point)
            GeozoneShape.Polygon(ring.reversed()).contains(point) shouldBe GeozoneShape.Polygon(ring).contains(point)
        }
    }
}
