package com.ttaaa.ultimate.analysis.grid

import com.ttaaa.ultimate.domain.AnalysisParameters
import com.ttaaa.ultimate.domain.GeoPoint
import com.ttaaa.ultimate.domain.RawRecord
import com.ttaaa.ultimate.domain.RawSession
import com.ttaaa.ultimate.domain.RecordingMode
import com.ttaaa.ultimate.domain.TimerEvent
import com.ttaaa.ultimate.domain.TimerEventType
import java.time.Duration
import java.time.Instant
import java.util.TreeMap

/**
 * Builds the 1 Hz grid of a session (spec 6.1 steps 2-4):
 *
 * 1. `t = 0` is the first record; for duplicate seconds the record that comes last wins.
 * 2. Speeds above `maxPlausibleSpeed` are outliers and count as missing. Outliers are removed before interpolating,
 *    so that no interpolated value is derived from an outlier.
 * 3. Seconds without a plausible speed are filled by linear interpolation between the nearest plausible samples when
 *    at most `maxInterpolationGapSec` seconds are missing (`smartRecordingMaxGapSec` for Smart recording) and the
 *    timer did not stop in between. Everything else stays a gap: a sample needs a speed.
 */
fun buildGrid(raw: RawSession, params: AnalysisParameters): Grid {
    require(raw.records.isNotEmpty()) { "A session needs at least one record" }
    val startTime = raw.records.minOf { it.timestamp }
    val records: TreeMap<Int, RawRecord> =
        raw.records.sortedBy { it.timestamp }.associateByTo(TreeMap()) { secondsSince(startTime, it.timestamp) }
    val stops = timerStops(raw.timerEvents, startTime)
    val recordingMode = recordingMode(records.keys.toList(), stops, params)
    val maxGapSec = when (recordingMode) {
        RecordingMode.EVERY_SECOND -> params.maxInterpolationGapSec
        RecordingMode.SMART -> params.smartRecordingMaxGapSec
    }

    val anchors = records.entries.filter { (_, record) -> record.speed.let { it != null && it <= params.maxPlausibleSpeed } }
    val points = ArrayList<GridPoint>(records.lastKey() + 1)
    anchors.forEachIndexed { i, (t, record) ->
        points += recordedPoint(startTime, t, record)
        val (nextT, nextRecord) = anchors.getOrNull(i + 1) ?: return@forEachIndexed
        val missing = nextT - t - 1
        if (missing in 1..maxGapSec && stops.none { it.overlaps(t, nextT) }) {
            for (u in t + 1..<nextT) {
                points += interpolatedPoint(startTime, u, t to record, nextT to nextRecord, records[u])
            }
        }
    }
    return Grid(startTime, lastT = records.lastKey(), recordingMode, points)
}

/**
 * [RecordingMode.EVERY_SECOND] when at least `everySecondRecordingMinShare` of the record intervals are 1 s.
 * Intervals across a timer stop do not count.
 */
internal fun recordingMode(recordTs: List<Int>, stops: List<TimerStop>, params: AnalysisParameters): RecordingMode {
    val intervals = recordTs.zipWithNext()
        .filter { (a, b) -> stops.none { it.overlaps(a, b) } }
        .map { (a, b) -> b - a }
    if (intervals.isEmpty()) return RecordingMode.EVERY_SECOND
    val oneSecondShare = intervals.count { it == 1 }.toDouble() / intervals.size
    return if (oneSecondShare >= params.everySecondRecordingMinShare) RecordingMode.EVERY_SECOND else RecordingMode.SMART
}

/** The timer was stopped from [fromT] until [toT]; `toT` is null when it was never restarted. */
internal data class TimerStop(val fromT: Int, val toT: Int?) {
    /** True when the timer was stopped at some time in `[a, b)`. */
    fun overlaps(a: Int, b: Int): Boolean = fromT < b && (toT == null || toT > a)
}

internal fun timerStops(events: List<TimerEvent>, startTime: Instant): List<TimerStop> {
    val stops = mutableListOf<TimerStop>()
    var stoppedAt: Int? = null
    for (event in events.sortedBy { it.timestamp }) {
        val t = secondsSince(startTime, event.timestamp)
        when (event.type) {
            TimerEventType.STOP -> if (stoppedAt == null) stoppedAt = t
            TimerEventType.START -> stoppedAt?.let {
                stops += TimerStop(it, t)
                stoppedAt = null
            }
        }
    }
    stoppedAt?.let { stops += TimerStop(it, null) }
    return stops
}

private fun recordedPoint(startTime: Instant, t: Int, record: RawRecord) = GridPoint(
    t = t,
    timestamp = startTime.plusSeconds(t.toLong()),
    position = record.position,
    speedRaw = record.speed,
    speed = checkNotNull(record.speed),
    hr = record.heartRate,
    distanceM = record.distanceM,
    altitudeM = record.altitudeM,
    interpolated = false,
)

/** A second between two plausible samples; values recorded at this second are kept, the rest is interpolated. */
private fun interpolatedPoint(
    startTime: Instant,
    t: Int,
    left: Pair<Int, RawRecord>,
    right: Pair<Int, RawRecord>,
    own: RawRecord?,
): GridPoint {
    val (leftT, a) = left
    val (rightT, b) = right
    val fraction = (t - leftT).toDouble() / (rightT - leftT)
    fun lerp(from: Double?, to: Double?): Double? = if (from == null || to == null) null else from + (to - from) * fraction

    return GridPoint(
        t = t,
        timestamp = startTime.plusSeconds(t.toLong()),
        position = own?.position ?: lerpPosition(a.position, b.position, fraction),
        speedRaw = own?.speed,
        speed = checkNotNull(lerp(a.speed, b.speed)),
        hr = own?.heartRate ?: lerp(a.heartRate?.toDouble(), b.heartRate?.toDouble())?.let { Math.round(it).toInt() },
        distanceM = own?.distanceM ?: lerp(a.distanceM, b.distanceM),
        altitudeM = own?.altitudeM ?: lerp(a.altitudeM, b.altitudeM),
        interpolated = true,
    )
}

private fun lerpPosition(from: GeoPoint?, to: GeoPoint?, fraction: Double): GeoPoint? {
    if (from == null || to == null) return null
    return GeoPoint(from.lat + (to.lat - from.lat) * fraction, from.lon + (to.lon - from.lon) * fraction)
}

private fun secondsSince(startTime: Instant, time: Instant): Int = Duration.between(startTime, time).seconds.toInt()
