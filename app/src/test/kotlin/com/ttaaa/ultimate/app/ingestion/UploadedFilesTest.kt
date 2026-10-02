package com.ttaaa.ultimate.app.ingestion

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class UploadedFilesTest {

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    private fun List<UploadItem>.summary() = map {
        when (it) {
            is UploadItem.Fit -> "${it.displayName} -> ${it.fileName} (${it.content.size} B)"
            is UploadItem.Rejected -> "${it.displayName}: ${it.reason}"
        }
    }

    @Test
    fun `plain files are FIT candidates whatever their name`() {
        unpack(listOf(UploadedFile("a.fit", ByteArray(10)), UploadedFile("notes.txt", ByteArray(3))), maxBytes = 100)
            .summary() shouldBe listOf("a.fit -> a.fit (10 B)", "notes.txt -> notes.txt (3 B)")
    }

    @Test
    fun `archives yield their FIT entries, case-insensitively and without macOS metadata`() {
        val archive = zip(
            "dir/" to ByteArray(0),
            "dir/one.FIT" to ByteArray(5),
            "__MACOSX/dir/._one.FIT" to ByteArray(2),
            "two.fit" to ByteArray(7),
            "readme.md" to ByteArray(1),
        )

        unpack(listOf(UploadedFile("export.zip", archive)), maxBytes = 100).summary() shouldBe listOf(
            "export.zip/dir/one.FIT -> one.FIT (5 B)",
            "export.zip/two.fit -> two.fit (7 B)",
        )
    }

    @Test
    fun `files and entries above the limit are rejected`() {
        val items = unpack(
            listOf(UploadedFile("big.fit", ByteArray(101)), UploadedFile("a.zip", zip("big.fit" to ByteArray(101)))),
            maxBytes = 100,
        )

        items.forEach { it.shouldBeInstanceOf<UploadItem.Rejected>() }
        items.summary().map { it.substringBefore(':') } shouldBe listOf("big.fit", "a.zip/big.fit")
    }

    @Test
    fun `archives without FIT files and broken archives are rejected`() {
        val broken = zip("one.fit" to ByteArray(5)).copyOf(30)

        unpack(listOf(UploadedFile("docs.zip", zip("readme.md" to ByteArray(1))), UploadedFile("broken.zip", broken)), 100)
            .summary() shouldBe listOf("docs.zip: No .fit file in the archive", "broken.zip: Not a valid zip archive")
    }
}
