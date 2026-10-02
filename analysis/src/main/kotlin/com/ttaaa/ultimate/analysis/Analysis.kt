package com.ttaaa.ultimate.analysis

import com.ttaaa.ultimate.analysis.effort.detectEfforts
import com.ttaaa.ultimate.analysis.grid.buildGrid
import com.ttaaa.ultimate.analysis.metrics.windowMetrics
import com.ttaaa.ultimate.analysis.pause.detectPauses
import com.ttaaa.ultimate.analysis.pause.markPauses
import com.ttaaa.ultimate.analysis.smoothing.smooth
import com.ttaaa.ultimate.domain.AnalysisParameters
import com.ttaaa.ultimate.domain.Effort
import com.ttaaa.ultimate.domain.GeoPoint
import com.ttaaa.ultimate.domain.Lap
import com.ttaaa.ultimate.domain.RawLap
import com.ttaaa.ultimate.domain.RawSession
import com.ttaaa.ultimate.domain.RecordingMode
import com.ttaaa.ultimate.domain.Sample
import com.ttaaa.ultimate.domain.TimeRange
import com.ttaaa.ultimate.domain.WindowMetrics
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

/** Everything the analysis derives from one raw session; deterministic for the same input and parameters. */
data class AnalysisResult(
    /** Time of the first record (`t = 0`). */
    val startTime: Instant,
    /** The session's time axis is `[0, lastT]`. */
    val lastT: Int,
    val recordingMode: RecordingMode,
    /** The 1 Hz series; a missing `t` is a gap. */
    val samples: List<Sample>,
    val pauses: List<TimeRange>,
    val efforts: List<Effort>,
    /** The watch laps on the session's time axis. */
    val laps: List<Lap>,
    /** Reference point for geozone matching: the FIT start position, else the first sample position (spec 6.6). */
    val referencePosition: GeoPoint?,
    /** Metrics of the whole session `[0, lastT]`. */
    val sessionMetrics: WindowMetrics,
)

/**
 * The analysis pipeline of spec 6.1: 1 Hz grid with gaps and outliers handled, smoothing and GPS acceleration,
 * pauses, efforts and the session metrics. Geozone matching ([com.ttaaa.ultimate.analysis.geo.matchGeozone]) and
 * segments are separate, because geozones and segments are user data that change without re-analysing a session.
 */
fun analyze(raw: RawSession, params: AnalysisParameters): AnalysisResult {
    val grid = buildGrid(raw, params)
    val smoothed = smooth(grid, params)
    val pauses = detectPauses(smoothed, params)
    val samples = markPauses(smoothed, pauses)
    val efforts = detectEfforts(samples, params)
    return AnalysisResult(
        startTime = grid.startTime,
        lastT = grid.lastT,
        recordingMode = grid.recordingMode,
        samples = samples,
        pauses = pauses,
        efforts = efforts,
        laps = lapsOnTimeAxis(raw.laps, grid.startTime, grid.lastT),
        referencePosition = raw.startPosition ?: samples.firstNotNullOfOrNull { it.position },
        sessionMetrics = windowMetrics(samples, efforts, TimeRange(0, grid.lastT), params),
    )
}

/** Laps in start order, with their times rounded to the nearest second and clamped to `[0, lastT]`. */
internal fun lapsOnTimeAxis(laps: List<RawLap>, startTime: Instant, lastT: Int): List<Lap> {
    fun t(time: Instant) = (Duration.between(startTime, time).toMillis() / 1000.0).roundToInt().coerceIn(0, lastT)
    return laps.sortedBy { it.startTime }.mapIndexed { index, lap ->
        val fromT = t(lap.startTime)
        Lap(index, TimeRange(fromT, maxOf(fromT, t(lap.endTime))), lap.trigger)
    }
}
