package com.ttaaa.ultimate.analysis

import com.ttaaa.ultimate.domain.GeoPoint
import com.ttaaa.ultimate.domain.RawRecord
import com.ttaaa.ultimate.domain.RawSession
import com.ttaaa.ultimate.domain.Sample
import com.ttaaa.ultimate.domain.TimerEvent
import com.ttaaa.ultimate.domain.TimerEventType
import java.time.Instant

/** Hand-built raw sessions for analysis tests; `t` is seconds from [T0]. */
object RawSessions {

    val T0: Instant = Instant.parse("2026-09-28T16:00:00Z")

    fun rawSession(records: List<RawRecord>, timerEvents: List<TimerEvent> = emptyList()) = RawSession(
        device = null,
        sport = "disc_golf",
        subSport = "ultimate",
        startTime = T0,
        totalElapsedSec = 0.0,
        totalTimerSec = 0.0,
        totalDistanceM = null,
        startPosition = null,
        localTzOffsetSec = null,
        records = records,
        laps = emptyList(),
        timerEvents = timerEvents,
    )

    fun record(
        t: Int,
        speed: Double? = 3.0,
        hr: Int? = null,
        position: GeoPoint? = null,
        distanceM: Double? = null,
    ) = RawRecord(T0.plusSeconds(t.toLong()), position, speed, hr, distanceM, altitudeM = null)

    fun everySecond(ts: IntRange, speed: Double = 3.0): List<RawRecord> = ts.map { record(it, speed) }

    fun timer(t: Int, type: TimerEventType) = TimerEvent(T0.plusSeconds(t.toLong()), type)

    /** A smoothed sample as the later pipeline steps see it. */
    fun sample(t: Int, speed: Double, interpolated: Boolean = false, inPause: Boolean = false, accel: Double = 0.0) =
        Sample(t, T0.plusSeconds(t.toLong()), null, speed, speed, accel, null, null, null, interpolated, inPause)

    /** Consecutive samples from [fromT], given as (seconds, speed) parts. */
    fun samples(fromT: Int, vararg parts: Pair<Int, Double>): List<Sample> {
        var t = fromT
        return parts.flatMap { (seconds, speed) -> List(seconds) { sample(t++, speed) } }
    }
}
