package com.ttaaa.ultimate.app.web

import com.ttaaa.ultimate.app.IntegrationTest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType

class SpaTest : IntegrationTest() {

    @Test
    fun `serves the SPA at the root and for client-side routes`() {
        listOf("/", "/sessions/0b0f6c4e-2d38-4b5a-9a43-6f1a3e2c9d11", "/drill-types", "/geozones").forEach { path ->
            val page = get(path)
            page.statusCode shouldBe HttpStatus.OK
            page.body!! shouldContain "<div id=\"root\"></div>"
            page.body!! shouldContain "<title>Ultimate Analytics</title>"
        }
    }

    @Test
    fun `backend paths and missing files are not answered with the SPA`() {
        val api = get("/api/unknown")
        api.statusCode shouldBe HttpStatus.NOT_FOUND
        api.headers.contentType shouldBe MediaType.APPLICATION_PROBLEM_JSON
        get("/assets/missing.js").statusCode shouldBe HttpStatus.NOT_FOUND
        get("/actuator/health").statusCode shouldBe HttpStatus.OK
    }
}
