package com.ttaaa.ultimate.app.web

import com.ttaaa.ultimate.app.IntegrationTest
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * The API contract (spec 12): the frontend generates its types from `frontend/openapi.json`, so the snapshot must equal
 * the API the backend serves. After an intended API change, run `./gradlew :app:test -PupdateGolden`; the frontend
 * build then shows every place the change breaks.
 */
class OpenApiSnapshotTest : IntegrationTest() {

    private val snapshot = Path.of(checkNotNull(System.getProperty("openapi.snapshot")))

    @Test
    fun `the frontend's OpenAPI snapshot matches the API`() {
        val actual = json.writerWithDefaultPrettyPrinter().writeValueAsString(get("/v3/api-docs").json) + "\n"

        if (System.getProperty("golden.update").toBoolean()) {
            snapshot.writeText(actual)
            return
        }
        withClue("frontend/openapi.json differs from the API; if intended, run with -PupdateGolden and rebuild the frontend") {
            snapshot.exists() shouldBe true
            actual shouldBe snapshot.readText()
        }
    }
}
