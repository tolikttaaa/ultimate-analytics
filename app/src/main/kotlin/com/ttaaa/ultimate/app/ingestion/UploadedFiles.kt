package com.ttaaa.ultimate.app.ingestion

import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.zip.ZipInputStream

/** A file as uploaded: a `.fit` file or a `.zip` archive of them (spec 7.4). */
class UploadedFile(val fileName: String, val content: ByteArray)

/** One result row of an upload: a FIT file to import, or why an uploaded file yields none. */
sealed interface UploadItem {
    /** Name shown in the upload result, `archive.zip/entry.fit` for files from an archive. */
    val displayName: String

    class Fit(override val displayName: String, val fileName: String, val content: ByteArray) : UploadItem

    class Rejected(override val displayName: String, val reason: String) : UploadItem
}

/**
 * Turns uploaded files into FIT files to import (spec 7.4 step 1): `.zip` archives are unpacked and every `.fit` in
 * them becomes its own item; everything else is taken as a FIT file. Each FIT file may be at most [maxBytes] long,
 * also when it comes out of an archive.
 */
fun unpack(files: List<UploadedFile>, maxBytes: Long): List<UploadItem> = files.flatMap { file ->
    if (isZip(file.content)) unzip(file, maxBytes) else listOf(fitItem(file.fileName, file.fileName, file.content, maxBytes))
}

private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

private fun isZip(content: ByteArray) = content.size >= ZIP_MAGIC.size && content.copyOf(ZIP_MAGIC.size).contentEquals(ZIP_MAGIC)

private fun fitItem(displayName: String, fileName: String, content: ByteArray, maxBytes: Long): UploadItem =
    if (content.size > maxBytes) {
        UploadItem.Rejected(displayName, "File is larger than ${maxBytes / (1024 * 1024)} MB")
    } else {
        UploadItem.Fit(displayName, fileName, content)
    }

private fun unzip(archive: UploadedFile, maxBytes: Long): List<UploadItem> {
    val items = mutableListOf<UploadItem>()
    try {
        ZipInputStream(ByteArrayInputStream(archive.content)).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                val fileName = entry.name.substringAfterLast('/')
                // Skip folders and the AppleDouble files macOS adds to archives (__MACOSX/._name.fit).
                val isFit = !entry.isDirectory && fileName.endsWith(".fit", ignoreCase = true) &&
                    !entry.name.startsWith("__MACOSX/") && !fileName.startsWith("._")
                if (isFit) {
                    // Read at most one byte more than allowed, so an oversized entry never fills the memory.
                    val content = zip.readNBytes(maxBytes.toInt() + 1)
                    items += fitItem("${archive.fileName}/${entry.name}", fileName, content, maxBytes)
                }
            }
        }
    } catch (_: IOException) {
        // ZipException for malformed archives, EOFException for truncated ones.
        return listOf(UploadItem.Rejected(archive.fileName, "Not a valid zip archive"))
    }
    return items.ifEmpty { listOf(UploadItem.Rejected(archive.fileName, "No .fit file in the archive")) }
}
