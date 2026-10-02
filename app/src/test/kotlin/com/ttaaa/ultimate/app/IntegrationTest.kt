package com.ttaaa.ultimate.app

import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.web.client.RestClient
import org.springframework.web.client.toEntity
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
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

    @Autowired
    protected lateinit var json: JsonMapper

    @LocalServerPort
    private var port: Int = 0

    private val client by lazy { RestClient.create("http://localhost:$port") }

    @BeforeEach
    fun emptyDatabaseAndRawFiles() {
        jdbc.sql("truncate session, geozone, drill_type cascade").update()
        RAW_DIR.toFile().deleteRecursively()
    }

    /** Calls the running application; any status comes back as a response instead of an exception. */
    protected fun call(
        method: HttpMethod,
        path: String,
        body: Any? = null,
        contentType: MediaType = MediaType.APPLICATION_JSON,
    ): ResponseEntity<String> {
        val request = client.method(method).uri(path)
        if (body != null) request.contentType(contentType).body(body)
        return request.retrieve().onStatus({ true }) { _, _ -> }.toEntity<String>()
    }

    protected fun get(path: String) = call(HttpMethod.GET, path)

    protected val ResponseEntity<String>.json: JsonNode get() = this@IntegrationTest.json.readTree(body)

    companion object {
        val RAW_DIR: Path = Path.of("build/test-storage/raw")
    }
}
