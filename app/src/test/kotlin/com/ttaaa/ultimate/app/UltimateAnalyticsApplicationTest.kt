package com.ttaaa.ultimate.app

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
class UltimateAnalyticsApplicationTest {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var jdbc: JdbcClient

    @Test
    fun `application starts and reports health UP`() {
        val health = RestClient.create("http://localhost:$port")
            .get().uri("/actuator/health")
            .retrieve()
            .body<String>()

        health shouldContain "\"status\":\"UP\""
    }

    @Test
    fun `flyway applies V1 from the db-migrations jar`() {
        val v1Success = jdbc.sql("select success from flyway_schema_history where version = '1'")
            .query(Boolean::class.java)
            .single()
        val tables = jdbc.sql(
            """
            select table_name from information_schema.tables
            where table_schema = 'public' and table_name <> 'flyway_schema_history'
            """.trimIndent(),
        )
            .query(String::class.java)
            .list()

        v1Success shouldBe true
        tables shouldContainExactlyInAnyOrder listOf(
            "drill_type", "geozone", "session", "sample", "lap", "segment", "effort", "metrics_snapshot",
        )
    }
}
