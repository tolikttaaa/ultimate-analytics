package com.ttaaa.ultimate.fit

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Golden FIT files and approval snapshots (spec 12). A changed output fails until the snapshot is updated on purpose
 * with `-PupdateGolden` and the diff is reviewed. The Gradle test task sets the directories.
 */
object Golden {

    /** The golden FIT files, `fit-parser/src/test/resources/fit`. */
    val fitDir: Path = directory("golden.fit.dir")

    /** The approved snapshots of the module under test. */
    val snapshotDir: Path = directory("golden.snapshot.dir")

    private val update = System.getProperty("golden.update").toBoolean()

    val fitFileNames: List<String>
        get() = Files.list(fitDir).use { files -> files.map { it.name }.filter { it.endsWith(".fit") }.sorted().toList() }

    fun fitFile(name: String): Path = fitDir.resolve(name)

    fun verify(snapshotName: String, actual: String) {
        val snapshot = snapshotDir.resolve(snapshotName)
        if (update) {
            Files.createDirectories(snapshot.parent)
            snapshot.writeText(actual)
            return
        }
        withClue("Golden snapshot $snapshotName differs; if intended, run with -PupdateGolden and review the diff") {
            snapshot.exists() shouldBe true
            actual shouldBe snapshot.readText()
        }
    }

    fun sha256(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).toHexString()

    private fun directory(property: String): Path =
        Path.of(checkNotNull(System.getProperty(property)) { "$property is set by the Gradle test task" })
}
