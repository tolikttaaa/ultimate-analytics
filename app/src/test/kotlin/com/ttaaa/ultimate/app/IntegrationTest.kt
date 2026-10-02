package com.ttaaa.ultimate.app

import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import java.nio.file.Path

/**
 * Base of the integration tests: the whole application against PostgreSQL in Testcontainers, with an empty database
 * and raw-file directory before every test. All subclasses share one Spring context and one container.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["app.storage.raw-dir=build/test-storage/raw"],
)
@Import(TestcontainersConfiguration::class)
abstract class IntegrationTest {

    @Autowired
    protected lateinit var jdbc: JdbcClient

    @BeforeEach
    fun emptyDatabaseAndRawFiles() {
        jdbc.sql("truncate session, geozone, drill_type cascade").update()
        RAW_DIR.toFile().deleteRecursively()
    }

    companion object {
        val RAW_DIR: Path = Path.of("build/test-storage/raw")
    }
}
