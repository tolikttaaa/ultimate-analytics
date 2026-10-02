package com.ttaaa.ultimate.domain

import java.time.Instant
import java.util.UUID

/** One uploaded training: one FIT file, one recorded activity (spec 3, 5). */
data class Session(
    val id: UUID,
    /** Lowercase hex SHA-256 of the raw FIT file; unique, used for deduplication. */
    val fileSha256: String,
    val fileName: String,
    val uploadedAt: Instant,
    /** Time of the first sample (`t = 0`), UTC. */
    val startTime: Instant,
    /** Offset of the watch's local time from UTC, used only for display (spec 11). */
    val localTzOffsetSec: Int?,
    val elapsedSec: Int,
    val timerSec: Int,
    val distanceM: Double?,
    val device: String?,
    val sport: String?,
    val subSport: String?,
    /**
     * Reference point for geozone matching: the FIT session start position, else the first valid sample
     * position (spec 6.6). Null when the session has no position at all.
     */
    val startPosition: GeoPoint?,
    val geozoneId: UUID?,
    val surface: Surface,
    val surfaceSource: SurfaceSource,
    val notes: String?,
    /** `ANALYSIS_VERSION` the stored analysis results were computed with (spec 8.2). */
    val analysisVersion: Int,
) {
    init {
        require(SHA256_HEX.matches(fileSha256)) { "Not a lowercase hex SHA-256: $fileSha256" }
        require(elapsedSec >= 0) { "Negative elapsed time: $elapsedSec" }
        require(timerSec >= 0) { "Negative timer time: $timerSec" }
    }

    private companion object {
        val SHA256_HEX = Regex("[0-9a-f]{64}")
    }
}
