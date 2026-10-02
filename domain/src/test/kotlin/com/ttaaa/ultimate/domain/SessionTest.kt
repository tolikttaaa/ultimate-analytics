package com.ttaaa.ultimate.domain

import io.kotest.assertions.throwables.shouldThrow
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class SessionTest {

    private fun session(fileSha256: String = "ab".repeat(32), elapsedSec: Int = 3600, timerSec: Int = 3500) = Session(
        id = UUID.randomUUID(),
        fileSha256 = fileSha256,
        fileName = "training.fit",
        uploadedAt = Instant.parse("2026-09-28T18:00:00Z"),
        startTime = Instant.parse("2026-09-28T16:02:11Z"),
        localTzOffsetSec = 10800,
        elapsedSec = elapsedSec,
        timerSec = timerSec,
        distanceM = 5200.0,
        device = "garmin forerunner",
        sport = null,
        subSport = null,
        startPosition = GeoPoint(34.68, 33.04),
        geozoneId = null,
        surface = Surface.UNKNOWN,
        surfaceSource = SurfaceSource.NONE,
        notes = null,
        analysisVersion = 1,
        recordingMode = RecordingMode.EVERY_SECOND,
    )

    @Test
    fun `accepts a lowercase hex SHA-256 only`() {
        session()
        shouldThrow<IllegalArgumentException> { session(fileSha256 = "AB".repeat(32)) }
        shouldThrow<IllegalArgumentException> { session(fileSha256 = "ab".repeat(31)) }
        shouldThrow<IllegalArgumentException> { session(fileSha256 = "xy".repeat(32)) }
    }

    @Test
    fun `rejects negative durations`() {
        shouldThrow<IllegalArgumentException> { session(elapsedSec = -1) }
        shouldThrow<IllegalArgumentException> { session(timerSec = -1) }
    }
}
