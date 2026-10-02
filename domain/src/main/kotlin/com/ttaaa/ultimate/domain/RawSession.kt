package com.ttaaa.ultimate.domain

import java.time.Instant

/**
 * The content of one FIT activity file, the input of the analysis (spec 4.1, 6.1 step 1). Produced by `fit-parser`;
 * values are in SI units and degrees, times are UTC. Records, laps and timer events keep the order of the file.
 */
data class RawSession(
    /** Manufacturer and product from `file_id`, e.g. `garmin fr965`. */
    val device: String?,
    /** FIT `session.sport`, lower case, e.g. `disc_golf`. */
    val sport: String?,
    /** FIT `session.sub_sport`, lower case, e.g. `ultimate`. */
    val subSport: String?,
    val startTime: Instant,
    val totalElapsedSec: Double,
    val totalTimerSec: Double,
    val totalDistanceM: Double?,
    /** `session.start_position`, if the watch had a position when the activity started. */
    val startPosition: GeoPoint?,
    /** Offset of the watch's local time from UTC (`activity.local_timestamp − activity.timestamp`). */
    val localTzOffsetSec: Int?,
    val records: List<RawRecord>,
    val laps: List<RawLap>,
    val timerEvents: List<TimerEvent>,
)

/** One FIT `record` message. */
data class RawRecord(
    val timestamp: Instant,
    val position: GeoPoint?,
    /** `enhanced_speed`, fallback `speed`, m/s. */
    val speed: Double?,
    /** bpm. */
    val heartRate: Int?,
    /** Cumulative distance, m. */
    val distanceM: Double?,
    /** `enhanced_altitude`, fallback `altitude`, m. */
    val altitudeM: Double?,
)

/** One FIT `lap` message. */
data class RawLap(
    val startTime: Instant,
    val endTime: Instant,
    /** FIT `lap_trigger`, lower case, e.g. `manual` or `session_end`. */
    val trigger: String?,
)

enum class TimerEventType { START, STOP }

/** A timer start or stop from the FIT `event` messages; the timer-stopped intervals are gaps (spec 6.1). */
data class TimerEvent(val timestamp: Instant, val type: TimerEventType)
