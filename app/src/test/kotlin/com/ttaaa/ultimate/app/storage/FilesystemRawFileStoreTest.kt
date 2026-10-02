package com.ttaaa.ultimate.app.storage

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class FilesystemRawFileStoreTest {

    @TempDir
    private lateinit var dir: Path

    private val sha256 = "ab".repeat(32)

    @Test
    fun `stores a file once under its hash`() {
        val store = FilesystemRawFileStore(dir.resolve("raw"))

        store.store(sha256, byteArrayOf(1, 2, 3)) shouldBe true
        store.store(sha256, byteArrayOf(1, 2, 3)) shouldBe false

        store.exists(sha256) shouldBe true
        store.read(sha256).toList() shouldBe listOf<Byte>(1, 2, 3)
        Files.list(dir.resolve("raw")).use { files -> files.map { it.fileName.toString() }.toList() } shouldBe
            listOf("$sha256.fit")
    }

    @Test
    fun `deletes files and reports missing ones`() {
        val store = FilesystemRawFileStore(dir)
        store.store(sha256, byteArrayOf(1))

        store.delete(sha256)
        store.delete(sha256)

        store.exists(sha256) shouldBe false
        shouldThrow<NoSuchElementException> { store.read(sha256) }
    }

    @Test
    fun `accepts only SHA-256 names`() {
        val store = FilesystemRawFileStore(dir)

        shouldThrow<IllegalArgumentException> { store.store("../etc/passwd", byteArrayOf(1)) }
        shouldThrow<IllegalArgumentException> { store.exists("AB".repeat(32)) }
    }
}
