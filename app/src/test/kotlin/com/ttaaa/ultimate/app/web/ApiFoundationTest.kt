package com.ttaaa.ultimate.app.web

import com.ttaaa.ultimate.app.IntegrationTest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType

class ApiFoundationTest : IntegrationTest() {

    @Test
    fun `publishes the OpenAPI description`() {
        val docs = get("/v3/api-docs")

        docs.statusCode shouldBe HttpStatus.OK
        docs.json["openapi"].asString() shouldStartWith "3."
        docs.json["info"]["title"].asString() shouldBe "Ultimate Analytics API"
    }

    @Test
    fun `the OpenAPI description has every endpoint of spec 9_1`() {
        val endpoints = listOf(
            "post /api/sessions/upload", "get /api/sessions",
            "get /api/sessions/{id}", "patch /api/sessions/{id}", "delete /api/sessions/{id}",
            "get /api/sessions/{id}/series", "get /api/sessions/{id}/efforts", "get /api/sessions/{id}/metrics",
            "get /api/sessions/{id}/file", "post /api/sessions/{id}/recompute", "post /api/sessions/recompute-outdated",
            "post /api/sessions/{id}/segments", "patch /api/sessions/{id}/segments/{segmentId}",
            "delete /api/sessions/{id}/segments/{segmentId}", "post /api/sessions/{id}/segments/{segmentId}/split",
            "post /api/sessions/{id}/segments/merge", "post /api/sessions/{id}/segments/reset-from-laps",
            "get /api/sessions/{id}/segments/{segmentId}/metrics",
            "get /api/drill-types", "post /api/drill-types", "patch /api/drill-types/{id}",
            "delete /api/drill-types/{id}", "get /api/drill-types/{id}/stats",
            "get /api/geozones", "post /api/geozones", "patch /api/geozones/{id}", "delete /api/geozones/{id}",
            "get /api/analysis/parameters",
        )
        val paths = get("/v3/api-docs").json["paths"]

        val missing = endpoints.filter { endpoint ->
            val (method, path) = endpoint.split(" ")
            paths[path]?.get(method) == null
        }

        missing shouldBe emptyList()
    }

    @Test
    fun `serves Swagger UI`() {
        val ui = get("/swagger-ui/index.html")

        ui.statusCode shouldBe HttpStatus.OK
        ui.body!! shouldContain "swagger-ui"
        val shortcut = get("/swagger-ui.html")
        shortcut.statusCode.is3xxRedirection shouldBe true
        shortcut.headers.location.toString() shouldContain "/swagger-ui/index.html"
    }

    @Test
    fun `the local profile lets requests through without login`() {
        get("/actuator/health").statusCode shouldBe HttpStatus.OK
        get("/v3/api-docs").statusCode shouldBe HttpStatus.OK
    }

    @Test
    fun `errors are problem details`() {
        val missing = get("/api/no-such-resource")

        missing.statusCode shouldBe HttpStatus.NOT_FOUND
        missing.headers.contentType shouldBe MediaType.APPLICATION_PROBLEM_JSON
        missing.json["status"].asInt() shouldBe 404
    }
}
