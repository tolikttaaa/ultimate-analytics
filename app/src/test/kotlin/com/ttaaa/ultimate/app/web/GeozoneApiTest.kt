package com.ttaaa.ultimate.app.web

import com.ttaaa.ultimate.app.IntegrationTest
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import tools.jackson.databind.JsonNode
import java.util.UUID

class GeozoneApiTest : IntegrationTest() {

    private val march = "22296100401_ACTIVITY.fit"
    private val september = "24557963847_ACTIVITY.fit" // starts about 8 km from the March session

    private fun circle(lat: Double, lon: Double, radiusM: Double) = mapOf("type" to "circle", "lat" to lat, "lon" to lon, "radiusM" to radiusM)

    private fun create(name: String, surface: String, shape: Any) =
        call(HttpMethod.POST, "/api/geozones", mapOf("name" to name, "surface" to surface, "shape" to shape))

    private fun session(id: UUID): JsonNode = get("/api/sessions/$id").json

    private fun JsonNode.classification() =
        Triple(this["surface"].asString(), this["surfaceSource"].asString(), this["geozoneName"].takeUnless { it.isNull }?.asString())

    @Test
    fun `creates, lists, edits and deletes geozones`() {
        val created = create("Field", "GRASS", circle(34.68, 33.04, 120.0))
        created.statusCode shouldBe HttpStatus.CREATED
        created.json["affectedSessionCount"].asInt() shouldBe 0
        val id = created.json["geozone"]["id"].asString()
        val ring = listOf(listOf(33.10, 34.70), listOf(33.11, 34.70), listOf(33.11, 34.71), listOf(33.10, 34.70))
        create("Beach", "SAND", mapOf("type" to "Polygon", "coordinates" to listOf(ring))).statusCode shouldBe HttpStatus.CREATED

        val list = get("/api/geozones").json.values().toList()
        list.map { it["name"].asString() } shouldBe listOf("Beach", "Field")
        list[0]["shape"]["coordinates"][0].size() shouldBe 4 // closed ring, as stored
        call(HttpMethod.PATCH, "/api/geozones/$id", mapOf("name" to "Main field")).json["geozone"]["name"].asString() shouldBe "Main field"

        create("Bad", "GRASS", circle(34.68, 33.04, -5.0)).statusCode shouldBe HttpStatus.BAD_REQUEST
        create("Bad", "UNKNOWN", circle(34.68, 33.04, 50.0)).statusCode shouldBe HttpStatus.BAD_REQUEST
        create("Bad", "SAND", mapOf("type" to "Polygon", "coordinates" to listOf(ring.take(2)))).statusCode shouldBe HttpStatus.BAD_REQUEST
        create("Bad", "SAND", mapOf("type" to "circle")).statusCode shouldBe HttpStatus.BAD_REQUEST
        create("Bad", "SAND", mapOf("type" to "square")).statusCode shouldBe HttpStatus.BAD_REQUEST

        call(HttpMethod.DELETE, "/api/geozones/$id").statusCode shouldBe HttpStatus.OK
        call(HttpMethod.DELETE, "/api/geozones/$id").statusCode shouldBe HttpStatus.NOT_FOUND
    }

    @Test
    fun `geozone changes classify the sessions again, except manual surfaces`() {
        val (marchId, septemberId) = uploadGolden(march, september)
        val start = session(septemberId)["startPosition"]
        val (lat, lon) = start["lat"].asDouble() to start["lon"].asDouble()

        val city = create("City", "GRASS", circle(lat, lon, 10_000.0)).json // both venues
        city["affectedSessionCount"].asInt() shouldBe 2
        session(marchId).classification() shouldBe Triple("GRASS", "GEOZONE", "City")
        val cityId = city["geozone"]["id"].asString()

        call(HttpMethod.PATCH, "/api/sessions/$marchId", mapOf("surface" to "GRASS"))
        call(HttpMethod.PATCH, "/api/geozones/$cityId", mapOf("surface" to "SAND")).json["affectedSessionCount"].asInt() shouldBe 1
        session(septemberId).classification() shouldBe Triple("SAND", "GEOZONE", "City")
        session(marchId).classification() shouldBe Triple("GRASS", "MANUAL", "City")

        // A smaller geozone inside wins.
        val court = create("Court", "GRASS", circle(lat, lon, 300.0)).json
        court["affectedSessionCount"].asInt() shouldBe 1
        session(septemberId).classification() shouldBe Triple("GRASS", "GEOZONE", "Court")

        call(HttpMethod.DELETE, "/api/geozones/${court["geozone"]["id"].asString()}").json["affectedSessionCount"].asInt() shouldBe 1
        session(septemberId).classification() shouldBe Triple("SAND", "GEOZONE", "City")
        call(HttpMethod.DELETE, "/api/geozones/$cityId").json["affectedSessionCount"].asInt() shouldBe 1
        session(septemberId).classification() shouldBe Triple("UNKNOWN", "NONE", null)
        session(marchId).classification() shouldBe Triple("GRASS", "MANUAL", null)
    }
}
