package com.ttaaa.ultimate.app

import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UltimateAnalyticsApplicationTest {

    @LocalServerPort
    private var port: Int = 0

    @Test
    fun `application starts and reports health UP`() {
        val health = RestClient.create("http://localhost:$port")
            .get().uri("/actuator/health")
            .retrieve()
            .body<String>()

        health shouldContain "\"status\":\"UP\""
    }
}
