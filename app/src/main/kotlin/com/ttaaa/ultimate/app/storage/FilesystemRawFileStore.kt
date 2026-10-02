package com.ttaaa.ultimate.app.storage

import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path

/** [RawFileStore] in a directory: `{rawDir}/{sha256}.fit` (spec 8.3). */
class FilesystemRawFileStore(private val rawDir: Path) : RawFileStore {

    override fun store(sha256: String, content: ByteArray): Boolean {
        val target = path(sha256)
        if (Files.exists(target)) return false
        Files.createDirectories(rawDir)
        // Write next to the target and rename, so a crash never leaves a partial file under the final name.
        val temp = Files.createTempFile(rawDir, "$sha256-", ".tmp")
        try {
            Files.write(temp, content)
            Files.move(temp, target)
            return true
        } catch (_: FileAlreadyExistsException) {
            return false
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    override fun exists(sha256: String): Boolean = Files.exists(path(sha256))

    override fun read(sha256: String): ByteArray {
        val file = path(sha256)
        if (!Files.exists(file)) throw NoSuchElementException("No raw file $sha256")
        return Files.readAllBytes(file)
    }

    override fun delete(sha256: String) {
        Files.deleteIfExists(path(sha256))
    }

    private fun path(sha256: String): Path {
        requireSha256(sha256)
        return rawDir.resolve("$sha256.fit")
    }
}
