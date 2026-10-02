package com.ttaaa.ultimate.app

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

class UltimateAnalyticsApplicationTest : IntegrationTest() {

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

    @Test
    fun `flyway applies the migrations from the db-migrations jar`() {
        val migrations = jdbc.sql("select version, success from flyway_schema_history order by installed_rank")
            .query { rs, _ -> rs.getString("version") to rs.getBoolean("success") }
            .list()
        val tables = jdbc.sql(
            """
            select table_name from information_schema.tables
            where table_schema = 'public' and table_name <> 'flyway_schema_history'
            """.trimIndent(),
        )
            .query(String::class.java)
            .list()

        migrations shouldBe listOf("1" to true, "2" to true)
        tables shouldContainExactlyInAnyOrder listOf(
            "drill_type", "geozone", "session", "sample", "lap", "segment", "effort", "metrics_snapshot",
        )
    }
}
