package com.ttaaa.ultimate.app.security

import com.ttaaa.ultimate.app.TestcontainersConfiguration
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalManagementPort
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.web.client.RestClient
import org.springframework.web.client.toEntity

/** The `server` profile with its configuration from infra/config: locked down until login exists (spec 7.5). */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.profiles.active=server",
        "spring.config.additional-location=optional:file:../infra/config/",
        "management.server.port=0",
    ],
)
@Import(TestcontainersConfiguration::class)
class ServerProfileTest {

    @LocalServerPort
    private var port: Int = 0

    @LocalManagementPort
    private var managementPort: Int = 0

    private fun status(port: Int, path: String) =
        RestClient.create("http://localhost:$port").get().uri(path).retrieve().onStatus({ true }) { _, _ -> }
            .toEntity<String>()

    @Test
    fun `the API is closed until login is implemented`() {
        status(port, "/api/sessions").statusCode.value() shouldBe 403
        status(port, "/v3/api-docs").statusCode.value() shouldBe 403
    }

    @Test
    fun `health probes answer on the management port`() {
        val readiness = status(managementPort, "/actuator/health/readiness")

        readiness.statusCode.value() shouldBe 200
        readiness.body!! shouldContain "UP"
        status(managementPort, "/actuator/health/liveness").statusCode.value() shouldBe 200
    }
}
