package com.ttaaa.ultimate.analysis

import com.ttaaa.ultimate.domain.Effort
import com.ttaaa.ultimate.domain.EffortMetrics
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
    fun sample(
        t: Int,
        speed: Double,
        interpolated: Boolean = false,
        inPause: Boolean = false,
        accel: Double = 0.0,
        hr: Int? = null,
    ) = Sample(t, T0.plusSeconds(t.toLong()), null, speed, speed, accel, hr, null, null, interpolated, inPause)

    /** An effort starting at [startT] with the given metrics; times and other metrics are placeholders. */
    fun effort(
        startT: Int,
        peakSpeed: Double = 6.0,
        meanSpeed: Double = 4.0,
        meanAccel: Double = 1.5,
        peakAccel: Double = 2.0,
        meanSpeedFirst3s: Double? = 4.0,
        timeTo80PctPeakSec: Double = 2.0,
    ) = Effort(
        startT, startT, startT,
        EffortMetrics(
            startSpeed = 0.0, peakSpeed = peakSpeed, timeToPeakSec = 1, durationSec = 1, distanceM = 0.0,
            meanSpeed = meanSpeed, meanAccel = meanAccel, peakAccel = peakAccel, speedAt1s = null, speedAt2s = null,
            speedAt3s = null, meanSpeedFirst3s = meanSpeedFirst3s, distanceFirst3s = 0.0,
            timeTo80PctPeakSec = timeTo80PctPeakSec, maxDecelAfter = 0.0, hrStart = null, hrMax = null,
        ),
    )

    /** Consecutive samples from [fromT], given as (seconds, speed) parts. */
    fun samples(fromT: Int, vararg parts: Pair<Int, Double>): List<Sample> {
        var t = fromT
        return parts.flatMap { (seconds, speed) -> List(seconds) { sample(t++, speed) } }
    }
}
