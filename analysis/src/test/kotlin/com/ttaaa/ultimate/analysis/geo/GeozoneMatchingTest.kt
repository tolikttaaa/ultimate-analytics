package com.ttaaa.ultimate.analysis.geo

import com.ttaaa.ultimate.analysis.geo.Places.FIELD
import com.ttaaa.ultimate.analysis.geo.Places.offset
import com.ttaaa.ultimate.domain.GeoPoint
import com.ttaaa.ultimate.domain.Geozone
import com.ttaaa.ultimate.domain.GeozoneShape
import com.ttaaa.ultimate.domain.Session
import com.ttaaa.ultimate.domain.Surface
import com.ttaaa.ultimate.domain.SurfaceSource
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.numericDouble
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class GeozoneMatchingTest {

    private fun circle(name: String, surface: Surface, radiusM: Double, center: GeoPoint = FIELD, id: UUID = UUID.randomUUID()) =
        Geozone(id, name, surface, GeozoneShape.Circle(center, radiusM))

    private val field = circle("Grass field", Surface.GRASS, radiusM = 150.0)
    private val park = circle("Beach park", Surface.SAND, radiusM = 1_000.0)

    private fun session(surface: Surface, source: SurfaceSource, geozoneId: UUID? = null) = Session(
        id = UUID.randomUUID(), fileSha256 = "ab".repeat(32), fileName = "training.fit",
        uploadedAt = Instant.parse("2026-09-28T18:00:00Z"), startTime = Instant.parse("2026-09-28T16:00:00Z"),
        localTzOffsetSec = null, elapsedSec = 3600, timerSec = 3600, distanceM = null, device = null, sport = null,
        subSport = null, startPosition = FIELD, geozoneId = geozoneId, surface = surface, surfaceSource = source,
        notes = null, analysisVersion = 1,
    )

    @Test
    fun `the smallest containing geozone wins`() {
        matchGeozone(FIELD.offset(northM = 50.0), listOf(park, field)) shouldBe field
        matchGeozone(FIELD.offset(northM = 500.0), listOf(park, field)) shouldBe park
        matchGeozone(FIELD.offset(northM = 2_000.0), listOf(park, field)) shouldBe null
        matchGeozone(null, listOf(park, field)) shouldBe null
    }

    @Test
    fun `equal areas are decided by id, not by order`() {
        val first = circle("A", Surface.GRASS, 100.0, id = UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val second = circle("B", Surface.SAND, 100.0, id = UUID.fromString("00000000-0000-0000-0000-000000000002"))

        matchGeozone(FIELD, listOf(second, first)) shouldBe first
        matchGeozone(FIELD, listOf(first, second)) shouldBe first
    }

    @Test
    fun `a match sets the surface, no match clears it, a manual surface stays`() {
        with(applyGeozoneMatch(session(Surface.UNKNOWN, SurfaceSource.NONE), field)) {
            surface shouldBe Surface.GRASS
            surfaceSource shouldBe SurfaceSource.GEOZONE
            geozoneId shouldBe field.id
        }
        with(applyGeozoneMatch(session(Surface.SAND, SurfaceSource.GEOZONE, park.id), null)) {
            surface shouldBe Surface.UNKNOWN
            surfaceSource shouldBe SurfaceSource.NONE
            geozoneId shouldBe null
        }
        val manual = session(Surface.SAND, SurfaceSource.MANUAL)
        applyGeozoneMatch(manual, field) shouldBe manual
    }

    @Test
    fun `the match contains the point and no containing geozone is smaller`() = runTest {
        val zones = Arb.bind(Arb.numericDouble(-500.0, 500.0), Arb.numericDouble(-500.0, 500.0), Arb.numericDouble(10.0, 800.0)) { n, e, r ->
            circle("zone", Surface.GRASS, r, center = FIELD.offset(n, e))
        }
        val points = Arb.bind(Arb.numericDouble(-800.0, 800.0), Arb.numericDouble(-800.0, 800.0)) { n, e -> FIELD.offset(n, e) }

        checkAll(Arb.list(zones, 0..6), points) { geozones, point ->
            val match = matchGeozone(point, geozones)
            val containing = geozones.filter { it.shape.contains(point) }

            (match == null) shouldBe containing.isEmpty()
            if (match != null) {
                match.shape.contains(point) shouldBe true
                containing.forEach { it.shape.areaM2() shouldBeGreaterThanOrEqual match.shape.areaM2() }
            }
        }
    }
}
