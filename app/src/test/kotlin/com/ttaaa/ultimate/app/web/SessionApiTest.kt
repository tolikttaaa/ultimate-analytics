package com.ttaaa.ultimate.app.web

import com.ttaaa.ultimate.app.IntegrationTest
import com.ttaaa.ultimate.fit.Golden
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import java.nio.file.Files
import java.util.UUID

class SessionApiTest : IntegrationTest() {

    private val march = "22296100401_ACTIVITY.fit" // 2026-03-25, two laps
    private val september = "24557963847_ACTIVITY.fit" // 2026-09-30

    private fun patch(id: UUID, body: Map<String, Any?>) = call(HttpMethod.PATCH, "/api/sessions/$id", body)

    @Test
    fun `uploads files and lists the sessions newest first`() {
        val upload = upload(march to Files.readAllBytes(Golden.fitFile(march)), "notes.txt" to "hello".toByteArray())

        upload.statusCode shouldBe HttpStatus.OK
        upload.json.values().map { it["status"].asString() } shouldBe listOf("CREATED", "FAILED")
        upload.json[0]["needsSurface"].asBoolean() shouldBe true
        val (septemberId) = uploadGolden(september)

        val list = get("/api/sessions")

        list.statusCode shouldBe HttpStatus.OK
        list.json["totalItems"].asInt() shouldBe 2
        val newest = list.json["items"][0]
        newest["id"].asString() shouldBe septemberId.toString()
        newest["startTime"].asString() shouldBe "2026-09-30T16:07:47Z"
        newest["recordingMode"].asString() shouldBe "SMART"
        newest["surface"].asString() shouldBe "UNKNOWN"
        newest["outdated"].asBoolean() shouldBe false
        newest["activeSec"].asInt() shouldBeGreaterThan 0
        newest["effortCount"].asInt() shouldBe 33
    }

    @Test
    fun `uploads all golden files in one request and reports duplicates the second time`() {
        val files = Golden.fitFileNames.map { it to Files.readAllBytes(Golden.fitFile(it)) }.toTypedArray()

        val first = upload(*files).json.values().map { it["status"].asString() }
        val second = upload(*files).json.values().map { it["status"].asString() }

        first shouldBe List(files.size) { "CREATED" }
        second shouldBe List(files.size) { "DUPLICATE" }
        get("/api/sessions?size=100").json["totalItems"].asInt() shouldBe files.size
    }

    @Test
    fun `filters by start time and surface and pages the results`() {
        val (marchId, septemberId) = uploadGolden(march, september)
        patch(septemberId, mapOf("surface" to "SAND"))
        fun ids(query: String) = get("/api/sessions?$query").json["items"].values().map { UUID.fromString(it["id"].asString()) }

        ids("from=2026-09-01T00:00:00Z") shouldBe listOf(septemberId)
        ids("to=2026-09-01T00:00:00Z") shouldBe listOf(marchId)
        ids("surface=SAND") shouldBe listOf(septemberId)
        ids("page=1&size=1") shouldBe listOf(marchId)
        get("/api/sessions?size=1").json["totalPages"].asInt() shouldBe 2

        val invalid = get("/api/sessions?size=0")
        invalid.statusCode shouldBe HttpStatus.BAD_REQUEST
        invalid.headers.contentType shouldBe MediaType.APPLICATION_PROBLEM_JSON
    }

    @Test
    fun `shows a session with its laps, segments and metrics`() {
        val (id) = uploadGolden(march)

        val detail = get("/api/sessions/$id").json

        detail["fileName"].asString() shouldBe march
        detail["laps"].values().map { it["endT"].asInt() } shouldBe listOf(3994, 7800)
        detail["segments"].values().map { it["label"].asString() } shouldBe listOf("Lap 1", "Lap 2")
        detail["metrics"]["time"]["elapsedSec"].asInt() shouldBe 7800
        detail["metrics"]["efforts"]["count"].asInt() shouldBe 26
        detail["outdated"].asBoolean() shouldBe false

        val missing = get("/api/sessions/${UUID.randomUUID()}")
        missing.statusCode shouldBe HttpStatus.NOT_FOUND
        missing.json["detail"].asString() shouldContain "not found"
    }

    @Test
    fun `sets a manual surface and edits the notes`() {
        val (id) = uploadGolden(march)

        patch(id, mapOf("surface" to "GRASS")).json["surfaceSource"].asString() shouldBe "MANUAL"
        patch(id, mapOf("notes" to "Windy, 7v7 at the end")).json["notes"].asString() shouldBe "Windy, 7v7 at the end"

        val unchanged = patch(id, emptyMap()).json
        unchanged["notes"].asString() shouldBe "Windy, 7v7 at the end"
        unchanged["surface"].asString() shouldBe "GRASS"
        patch(id, mapOf("notes" to null)).json["notes"].isNull shouldBe true
        patch(id, mapOf("surface" to "LAVA")).statusCode shouldBe HttpStatus.BAD_REQUEST
    }

    @Test
    fun `deletes a session with its raw file`() {
        val (id) = uploadGolden(march)

        call(HttpMethod.DELETE, "/api/sessions/$id").statusCode shouldBe HttpStatus.NO_CONTENT

        get("/api/sessions/$id").statusCode shouldBe HttpStatus.NOT_FOUND
        Files.list(RAW_DIR).use { it.count() } shouldBe 0
        call(HttpMethod.DELETE, "/api/sessions/$id").statusCode shouldBe HttpStatus.NOT_FOUND
    }

    @Test
    fun `downloads the original file`() {
        val (id) = uploadGolden(march)

        val file = call(HttpMethod.GET, "/api/sessions/$id/file")

        file.statusCode shouldBe HttpStatus.OK
        file.headers.getFirst("Content-Disposition") shouldBe "attachment; filename=\"$march\""
        file.body!!.toByteArray(Charsets.ISO_8859_1).size shouldBe Files.size(Golden.fitFile(march)).toInt()
    }
}
