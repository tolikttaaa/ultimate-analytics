package com.ttaaa.ultimate.app.web

import com.ttaaa.ultimate.app.IntegrationTest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import tools.jackson.databind.JsonNode
import java.util.UUID

class SegmentApiTest : IntegrationTest() {

    private val march = "22296100401_ACTIVITY.fit" // laps [0, 3994] and [3994, 7800], 26 efforts

    private fun segments(id: UUID): List<JsonNode> = get("/api/sessions/$id").json["segments"].values().toList()

    private fun JsonNode.range() = this["startT"].asInt() to this["endT"].asInt()

    private fun post(path: String, body: Any? = null) = call(HttpMethod.POST, path, body)

    private fun patch(path: String, body: Any) = call(HttpMethod.PATCH, path, body)

    /** Every effort is linked to the segment containing its start, or to none. */
    private fun effortsFollowSegments(id: UUID) {
        val ranges = segments(id).associate { it["id"].asString() to it.range() }
        get("/api/sessions/$id/efforts").json.values().forEach { effort ->
            val start = effort["startT"].asInt()
            val expected = ranges.entries.firstOrNull { (_, range) -> start >= range.first && start < range.second }?.key
            (if (effort["segmentId"].isNull) null else effort["segmentId"].asString()) shouldBe expected
        }
    }

    private fun drillType(code: String): UUID {
        val id = UUID.randomUUID()
        jdbc.sql("insert into drill_type (id, code, name, kind, color) values (:id, :code, :code, 'DRILL', '#2E7D32')")
            .param("id", id)
            .param("code", code)
            .update()
        return id
    }

    @Test
    fun `creates segments and rejects overlapping or invalid ones`() {
        val (id) = uploadGolden(march)
        val lap2 = segments(id).last()["id"].asString()
        call(HttpMethod.DELETE, "/api/sessions/$id/segments/$lap2").statusCode shouldBe HttpStatus.NO_CONTENT

        val created = post("/api/sessions/$id/segments", mapOf("startT" to 4000, "endT" to 4600, "label" to " Cutting "))

        created.statusCode shouldBe HttpStatus.CREATED
        created.json["label"].asString() shouldBe "Cutting"
        created.json["source"].asString() shouldBe "MANUAL"
        effortsFollowSegments(id)
        val overlap = post("/api/sessions/$id/segments", mapOf("startT" to 4500, "endT" to 5000))
        overlap.statusCode shouldBe HttpStatus.CONFLICT
        overlap.json["detail"].asString() shouldContain "overlaps"
        post("/api/sessions/$id/segments", mapOf("startT" to 7000, "endT" to 7900)).statusCode shouldBe HttpStatus.BAD_REQUEST
        post("/api/sessions/$id/segments", mapOf("startT" to 5000, "endT" to 5005)).statusCode shouldBe HttpStatus.BAD_REQUEST
        post("/api/sessions/$id/segments", mapOf("startT" to 5000, "endT" to 5600, "drillTypeId" to UUID.randomUUID()))
            .statusCode shouldBe HttpStatus.BAD_REQUEST
    }

    @Test
    fun `edits bounds, drill type and label`() {
        val (id) = uploadGolden(march)
        val (lap1, lap2) = segments(id).map { it["id"].asString() }
        val cutting = drillType("CUTTING_1V1")

        val typed = patch("/api/sessions/$id/segments/$lap2", mapOf("drillTypeId" to cutting, "label" to "Cutting")).json
        typed["drillTypeId"].asString() shouldBe cutting.toString()
        typed["source"].asString() shouldBe "LAP" // type and label only

        val shrunk = patch("/api/sessions/$id/segments/$lap2", mapOf("startT" to 5000)).json
        shrunk.range() shouldBe (5000 to 7800)
        shrunk["source"].asString() shouldBe "MANUAL"
        shrunk["label"].asString() shouldBe "Cutting"
        get("/api/sessions/$id/segments/$lap2/metrics").json["time"]["elapsedSec"].asInt() shouldBe 2800
        effortsFollowSegments(id)

        patch("/api/sessions/$id/segments/$lap2", mapOf("label" to null, "drillTypeId" to null)).json.let {
            it["label"].isNull shouldBe true
            it["drillTypeId"].isNull shouldBe true
        }
        patch("/api/sessions/$id/segments/$lap1", mapOf("endT" to 5100)).statusCode shouldBe HttpStatus.CONFLICT
        patch("/api/sessions/$id/segments/${UUID.randomUUID()}", mapOf("label" to "x")).statusCode shouldBe HttpStatus.NOT_FOUND
    }

    @Test
    fun `splits and merges segments`() {
        val (id) = uploadGolden(march)
        val (lap1, lap2) = segments(id).map { it["id"].asString() }

        val parts = post("/api/sessions/$id/segments/$lap1/split", mapOf("atT" to 2000)).json.values().toList()

        parts.map { it.range() } shouldBe listOf(0 to 2000, 2000 to 3994)
        parts.map { it["label"].asString() } shouldBe listOf("Lap 1", "Lap 1")
        effortsFollowSegments(id)
        post("/api/sessions/$id/segments/$lap1/split", mapOf("atT" to 2005)).statusCode shouldBe HttpStatus.BAD_REQUEST

        val partIds = parts.map { it["id"].asString() }
        post("/api/sessions/$id/segments/merge", mapOf("segmentIds" to listOf(partIds[0], lap2))).statusCode shouldBe
            HttpStatus.BAD_REQUEST // the second part lies between them
        val merged = post("/api/sessions/$id/segments/merge", mapOf("segmentIds" to partIds.reversed())).json

        merged["id"].asString() shouldBe partIds[0]
        merged.range() shouldBe (0 to 3994)
        segments(id).map { it.range() } shouldBe listOf(0 to 3994, 3994 to 7800)
        effortsFollowSegments(id)
    }

    @Test
    fun `resets the segments from the laps`() {
        val (id) = uploadGolden(march)
        val lap1 = segments(id).first()["id"].asString()
        post("/api/sessions/$id/segments/$lap1/split", mapOf("atT" to 1000))

        post("/api/sessions/$id/segments/reset-from-laps").statusCode shouldBe HttpStatus.BAD_REQUEST
        val reset = post("/api/sessions/$id/segments/reset-from-laps?confirm=true").json.values().toList()

        reset.map { it["label"].asString() to it["source"].asString() } shouldBe listOf("Lap 1" to "LAP", "Lap 2" to "LAP")
        segments(id).map { it.range() } shouldBe listOf(0 to 3994, 3994 to 7800)
        effortsFollowSegments(id)
    }

    @Test
    fun `segment metrics are the metrics of the segment's window`() {
        val (id) = uploadGolden(march)
        val lap2 = segments(id).last()["id"].asString()

        get("/api/sessions/$id/segments/$lap2/metrics").json shouldBe get("/api/sessions/$id/metrics?from=3994&to=7800").json
    }

    @Test
    fun `overwriting splits a segment around the new one`() {
        val (id) = uploadGolden(march)
        val (lap1, lap2) = segments(id).map { it["id"].asString() }

        val created = post("/api/sessions/$id/segments?overwrite=true", mapOf("startT" to 600, "endT" to 1200, "label" to "Cutting"))

        created.statusCode shouldBe HttpStatus.CREATED
        segments(id).map { listOf(it.range(), it["label"].asString(), it["source"].asString()) } shouldBe listOf(
            listOf(0 to 600, "Lap 1", "MANUAL"),
            listOf(600 to 1200, "Cutting", "MANUAL"),
            listOf(1200 to 3994, "Lap 1", "MANUAL"),
            listOf(3994 to 7800, "Lap 2", "LAP"),
        )
        segments(id)[0]["id"].asString() shouldBe lap1
        segments(id).last()["id"].asString() shouldBe lap2
        effortsFollowSegments(id)
    }

    @Test
    fun `overwriting trims the segments at both ends and drops slivers`() {
        val (id) = uploadGolden(march)

        post("/api/sessions/$id/segments?overwrite=true", mapOf("startT" to 3000, "endT" to 5000)).statusCode shouldBe HttpStatus.CREATED
        segments(id).map { it.range() } shouldBe listOf(0 to 3000, 3000 to 5000, 5000 to 7800)

        // 0..5 would be left of the first segment: shorter than 10 s, so it goes.
        post("/api/sessions/$id/segments?overwrite=true", mapOf("startT" to 5, "endT" to 3000)).statusCode shouldBe HttpStatus.CREATED
        segments(id).map { it.range() } shouldBe listOf(5 to 3000, 3000 to 5000, 5000 to 7800)
        effortsFollowSegments(id)
    }

    @Test
    fun `overwriting removes covered segments, and without it an overlap is still a conflict`() {
        val (id) = uploadGolden(march)

        post("/api/sessions/$id/segments", mapOf("startT" to 0, "endT" to 7800)).statusCode shouldBe HttpStatus.CONFLICT
        post("/api/sessions/$id/segments?overwrite=true", mapOf("startT" to 0, "endT" to 7800, "label" to "Everything"))

        segments(id).map { it.range() to it["label"].asString() } shouldBe listOf((0 to 7800) to "Everything")
        effortsFollowSegments(id)
    }

    @Test
    fun `a session with a single lap starts without segments`() {
        val (id) = uploadGolden("24557963847_ACTIVITY.fit")

        segments(id) shouldBe emptyList()
        post("/api/sessions/$id/segments/reset-from-laps?confirm=true").json.values().toList() shouldBe emptyList()
        post("/api/sessions/$id/segments", mapOf("startT" to 600, "endT" to 1200)).statusCode shouldBe HttpStatus.CREATED
    }
}
