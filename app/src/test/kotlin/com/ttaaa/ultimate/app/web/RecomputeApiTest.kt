package com.ttaaa.ultimate.app.web

import com.ttaaa.ultimate.analysis.ANALYSIS_VERSION
import com.ttaaa.ultimate.app.IntegrationTest
import com.ttaaa.ultimate.app.metrics.MetricsSnapshotRepository
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.util.UUID

class RecomputeApiTest : IntegrationTest() {

    @Autowired private lateinit var snapshots: MetricsSnapshotRepository

    private val march = "22296100401_ACTIVITY.fit" // 26 efforts

    private fun post(path: String, body: Any? = null) = call(HttpMethod.POST, path, body)

    /** Marks a session as analysed by an older version and damages its derived data. */
    private fun makeOutdated(id: UUID) {
        jdbc.sql("update session set analysis_version = 0 where id = :id").param("id", id).update()
        jdbc.sql("delete from effort where session_id = :id").param("id", id).update()
        jdbc.sql("delete from metrics_snapshot where session_id = :id").param("id", id).update()
    }

    @Test
    fun `recomputing keeps segments, drill types, notes and a manual surface`() {
        val (id) = uploadGolden(march)
        val lap1 = get("/api/sessions/$id").json["segments"][0]["id"].asString()
        post("/api/sessions/$id/segments/$lap1/split", mapOf("atT" to 2000))
        val drillType = post("/api/drill-types", mapOf("code" to "CUTTING", "name" to "Cutting", "color" to "#2E7D32", "kind" to "DRILL"))
            .json["id"].asString()
        call(HttpMethod.PATCH, "/api/sessions/$id/segments/$lap1", mapOf("drillTypeId" to drillType, "label" to "Warm cuts"))
        call(HttpMethod.PATCH, "/api/sessions/$id", mapOf("surface" to "SAND", "notes" to "Strong wind"))
        val before = get("/api/sessions/$id").json
        makeOutdated(id)
        get("/api/sessions").json["items"][0]["outdated"].asBoolean() shouldBe true

        val result = post("/api/sessions/recompute-outdated")

        result.json["count"].asInt() shouldBe 1
        val after = get("/api/sessions/$id").json
        after["outdated"].asBoolean() shouldBe false
        after["analysisVersion"].asInt() shouldBe ANALYSIS_VERSION
        after["segments"] shouldBe before["segments"]
        after["surface"].asString() shouldBe "SAND"
        after["surfaceSource"].asString() shouldBe "MANUAL"
        after["notes"].asString() shouldBe "Strong wind"
        after["metrics"] shouldBe before["metrics"]
        val efforts = get("/api/sessions/$id/efforts").json.values().toList()
        efforts.size shouldBe 26
        efforts.count { it["segmentId"].asString() == lap1 } shouldBe efforts.count { it["startT"].asInt() < 2000 }
        snapshots.countBySession(id) shouldBe 1 + after["segments"].size()
        post("/api/sessions/recompute-outdated").json["count"].asInt() shouldBe 0
    }

    @Test
    fun `recomputing an up-to-date session gives identical results`() {
        val (id) = uploadGolden(march)
        val series = get("/api/sessions/$id/series").json
        val efforts = get("/api/sessions/$id/efforts").json.values().map { it["metrics"] }

        val recomputed = post("/api/sessions/$id/recompute")

        recomputed.statusCode shouldBe HttpStatus.OK
        recomputed.json["id"].asString() shouldBe id.toString()
        get("/api/sessions/$id/series").json shouldBe series
        get("/api/sessions/$id/efforts").json.values().map { it["metrics"] } shouldBe efforts
    }

    @Test
    fun `recomputing needs the session and its raw file`() {
        post("/api/sessions/${UUID.randomUUID()}/recompute").statusCode shouldBe HttpStatus.NOT_FOUND
        val (id) = uploadGolden(march)
        RAW_DIR.toFile().deleteRecursively()

        post("/api/sessions/$id/recompute").statusCode shouldBe HttpStatus.NOT_FOUND
    }

    @Test
    fun `publishes the current analysis parameters`() {
        val parameters = get("/api/analysis/parameters").json

        parameters["analysisVersion"].asInt() shouldBe ANALYSIS_VERSION
        parameters["parameters"]["sgWindow"].asInt() shouldBe 5
        parameters["parameters"]["hrMax"].asInt() shouldBe 190
        parameters["parameters"]["speedZones"].size() shouldBe 5
    }
}
