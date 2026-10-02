package com.ttaaa.ultimate.app.storage

/**
 * Storage of the original FIT files, keyed by their SHA-256 (spec 8.3). Raw files are written once and never modified;
 * everything else can be recomputed from them.
 */
interface RawFileStore {

    /** Stores [content] under [sha256]; false if a file with this hash was already stored. */
    fun store(sha256: String, content: ByteArray): Boolean

    fun exists(sha256: String): Boolean

    /** The stored file; [NoSuchElementException] if there is none. */
    fun read(sha256: String): ByteArray

    /** Removes the file if it exists. */
    fun delete(sha256: String)
}

internal fun requireSha256(sha256: String) {
    require(sha256.matches(Regex("[0-9a-f]{64}"))) { "Not a lowercase hex SHA-256: $sha256" }
}
