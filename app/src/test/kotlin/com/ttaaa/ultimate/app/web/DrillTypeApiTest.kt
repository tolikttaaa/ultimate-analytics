package com.ttaaa.ultimate.app.web

import com.ttaaa.ultimate.app.IntegrationTest
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.util.UUID

class DrillTypeApiTest : IntegrationTest() {

    private val march = "22296100401_ACTIVITY.fit" // laps [0, 3994] and [3994, 7800]
    private val september = "24557963847_ACTIVITY.fit" // one lap [0, 6566]

    private fun create(code: String, name: String = code, color: String = "#2E7D32", kind: String = "DRILL") =
        call(HttpMethod.POST, "/api/drill-types", mapOf("code" to code, "name" to name, "color" to color, "kind" to kind))

    private fun typeFirstSegment(sessionId: UUID, drillTypeId: String): String {
        val segmentId = get("/api/sessions/$sessionId").json["segments"][0]["id"].asString()
        call(HttpMethod.PATCH, "/api/sessions/$sessionId/segments/$segmentId", mapOf("drillTypeId" to drillTypeId))
        return segmentId
    }

    @Test
    fun `creates, lists, edits and deletes drill types`() {
        val created = create(" cutting_1v1 ", name = "Cutting 1v1")
        created.statusCode shouldBe HttpStatus.CREATED
        created.json["code"].asString() shouldBe "CUTTING_1V1"
        val id = created.json["id"].asString()
        create("GAME", kind = "GAME")

        get("/api/drill-types").json.values().map { it["name"].asString() } shouldBe listOf("Cutting 1v1", "GAME")
        val edited = call(HttpMethod.PATCH, "/api/drill-types/$id", mapOf("color" to "#1565C0", "name" to "Cuts")).json
        edited["color"].asString() shouldBe "#1565C0"
        edited["code"].asString() shouldBe "CUTTING_1V1"

        create("game").statusCode shouldBe HttpStatus.CONFLICT
        call(HttpMethod.PATCH, "/api/drill-types/$id", mapOf("code" to "GAME")).statusCode shouldBe HttpStatus.CONFLICT
        create("WARMUP", color = "green").statusCode shouldBe HttpStatus.BAD_REQUEST
        create("WARM UP").statusCode shouldBe HttpStatus.BAD_REQUEST

        call(HttpMethod.DELETE, "/api/drill-types/$id").statusCode shouldBe HttpStatus.NO_CONTENT
        call(HttpMethod.DELETE, "/api/drill-types/$id").statusCode shouldBe HttpStatus.NOT_FOUND
    }

    @Test
    fun `deleting a drill type leaves its segments untyped`() {
        val (sessionId) = uploadGolden(march)
        val typeId = create("CUTTING").json["id"].asString()
        typeFirstSegment(sessionId, typeId)

        call(HttpMethod.DELETE, "/api/drill-types/$typeId")

        get("/api/sessions/$sessionId").json["segments"][0]["drillTypeId"].isNull shouldBe true
    }

    @Test
    fun `aggregates the segments of a drill type across sessions`() {
        val (marchId, septemberId) = uploadGolden(march, september)
        val typeId = create("CUTTING").json["id"].asString()
        val marchSegment = typeFirstSegment(marchId, typeId)
        val septemberSegment = typeFirstSegment(septemberId, typeId)
        call(HttpMethod.PATCH, "/api/sessions/$septemberId", mapOf("surface" to "SAND"))
        val marchMetrics = get("/api/sessions/$marchId/segments/$marchSegment/metrics").json
        val septemberMetrics = get("/api/sessions/$septemberId/segments/$septemberSegment/metrics").json

        val stats = get("/api/drill-types/$typeId/stats").json

        stats["drillType"]["code"].asString() shouldBe "CUTTING"
        stats["sessions"].values().map { it["sessionId"].asString() } shouldBe listOf(marchId.toString(), septemberId.toString())
        stats["sessions"][1]["surface"].asString() shouldBe "SAND"
        stats["sessions"][0]["metrics"]["efforts"]["count"].asInt() shouldBe marchMetrics["efforts"]["count"].asInt()
        val totals = stats["totals"]
        totals["time"]["elapsedSec"].asInt() shouldBe 3994 + 6566
        totals["efforts"]["count"].asInt() shouldBe
            marchMetrics["efforts"]["count"].asInt() + septemberMetrics["efforts"]["count"].asInt()
        totals["distance"]["maxSpeed"].asDouble() shouldBe
            maxOf(marchMetrics["distance"]["maxSpeed"].asDouble(), septemberMetrics["distance"]["maxSpeed"].asDouble())
        // Effort means are weighted by effort count.
        val counts = listOf(marchMetrics, septemberMetrics).map { it["efforts"]["count"].asDouble() }
        val means = listOf(marchMetrics, septemberMetrics).map { it["efforts"]["peakSpeed"]["mean"].asDouble() }
        totals["efforts"]["peakSpeed"]["mean"].asDouble() shouldBe
            ((counts[0] * means[0] + counts[1] * means[1]) / counts.sum() plusOrMinus 1e-9)

        fun sessionIds(query: String) =
            get("/api/drill-types/$typeId/stats?$query").json["sessions"].values().map { it["sessionId"].asString() }
        sessionIds("surface=SAND") shouldBe listOf(septemberId.toString())
        sessionIds("to=2026-09-01T00:00:00Z") shouldBe listOf(marchId.toString())
        get("/api/drill-types/${UUID.randomUUID()}/stats").statusCode shouldBe HttpStatus.NOT_FOUND
    }
}
