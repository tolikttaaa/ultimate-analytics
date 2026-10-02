package com.ttaaa.ultimate.app.web

import com.ttaaa.ultimate.app.IntegrationTest
import io.kotest.matchers.shouldBe
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
