package com.ttaaa.ultimate.fit

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Approval snapshots for the golden FIT files (spec 12). A changed output fails until the snapshot is updated on
 * purpose with `-PupdateGolden` and the diff is reviewed.
 */
object Golden {

    val dir: Path = Path.of(checkNotNull(System.getProperty("golden.dir")) { "golden.dir is set by the Gradle test task" })

    private val update = System.getProperty("golden.update").toBoolean()

    val fitFiles: List<Path>
        get() = Files.list(dir).use { files -> files.filter { it.toString().endsWith(".fit") }.sorted().toList() }

    fun verify(snapshot: Path, actual: String) {
        if (update) {
            Files.createDirectories(snapshot.parent)
            snapshot.writeText(actual)
            return
        }
        withClue("Golden snapshot ${dir.relativize(snapshot)} differs; if intended, run with -PupdateGolden and review") {
            snapshot.exists() shouldBe true
            actual shouldBe snapshot.readText()
        }
    }
}
