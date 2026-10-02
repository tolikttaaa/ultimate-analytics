package com.ttaaa.ultimate.fit

import com.garmin.fit.ActivityMesg
import com.garmin.fit.DateTime
import com.garmin.fit.Decode
import com.garmin.fit.Event
import com.garmin.fit.EventMesg
import com.garmin.fit.EventType
import com.garmin.fit.FileIdMesg
import com.garmin.fit.FitDecoder
import com.garmin.fit.FitRuntimeException
import com.garmin.fit.GarminProduct
import com.garmin.fit.LapMesg
import com.garmin.fit.Manufacturer
import com.garmin.fit.RecordMesg
import com.garmin.fit.SessionMesg
import com.garmin.fit.Sport
import com.garmin.fit.SubSport
import com.ttaaa.ultimate.domain.GeoPoint
import com.ttaaa.ultimate.domain.RawLap
import com.ttaaa.ultimate.domain.RawRecord
import com.ttaaa.ultimate.domain.RawSession
import com.ttaaa.ultimate.domain.TimerEvent
import com.ttaaa.ultimate.domain.TimerEventType
import java.io.InputStream
import java.time.Duration
import java.time.Instant
import com.garmin.fit.File as FitFileType

/** Parses FIT activity files with the Garmin FIT SDK (spec 4.1). */
object FitParser {

    /** The Ultimate Disc activity profile writes `sport = disc_golf`, `sub_sport = ultimate`. */
    val ULTIMATE_SPORT: Sport = Sport.DISC_GOLF
    val ULTIMATE_SUB_SPORT: SubSport = SubSport.ULTIMATE

    private val FIT_EPOCH: Instant = Instant.parse("1989-12-31T00:00:00Z")
    private const val DEGREES_PER_SEMICIRCLE = 180.0 / (1L shl 31)
    private val logger = System.getLogger(FitParser::class.java.name)

    /**
     * Reads the whole [input] and returns the session it records. Activities of other sports are parsed as well,
     * with a logged warning.
     *
     * @throws FitParseException if the file is not a valid FIT activity with a session and records.
     */
    fun parse(input: InputStream): RawSession {
        val bytes = input.readAllBytes()
        val messages = try {
            if (!Decode().isFileFit(bytes.inputStream())) throw FitParseException.NotAFitFile()
            if (!Decode().checkFileIntegrity(bytes.inputStream())) throw FitParseException.Corrupted()
            FitDecoder().decode(bytes.inputStream())
        } catch (e: FitRuntimeException) {
            throw FitParseException.Corrupted(e)
        }

        val fileId = messages.fileIdMesgs.firstOrNull()
        if (fileId?.type != FitFileType.ACTIVITY) throw FitParseException.NotAnActivity(fileId?.type?.lowerName())
        val session = messages.sessionMesgs.firstOrNull() ?: throw FitParseException.MissingData("session")
        val records = messages.recordMesgs.mapNotNull(::toRawRecord)
        if (records.isEmpty()) throw FitParseException.MissingData("records")
        val startTime = session.startTime?.toInstant() ?: records.first().timestamp

        if (session.sport != ULTIMATE_SPORT || session.subSport != ULTIMATE_SUB_SPORT) {
            logger.log(
                System.Logger.Level.WARNING,
                "FIT activity is ${session.sport}/${session.subSport}, not an Ultimate Disc activity; parsing anyway",
            )
        }

        return RawSession(
            device = device(fileId),
            sport = session.sport?.takeIf { it != Sport.INVALID }?.lowerName(),
            subSport = session.subSport?.takeIf { it != SubSport.INVALID }?.lowerName(),
            startTime = startTime,
            totalElapsedSec = session.double(SessionMesg.TotalElapsedTimeFieldNum) ?: 0.0,
            totalTimerSec = session.double(SessionMesg.TotalTimerTimeFieldNum) ?: 0.0,
            totalDistanceM = session.double(SessionMesg.TotalDistanceFieldNum),
            startPosition = geoPoint(session.startPositionLat, session.startPositionLong),
            localTzOffsetSec = messages.activityMesgs.firstOrNull()?.let(::localTzOffsetSec),
            records = records,
            laps = messages.lapMesgs.mapNotNull(::toRawLap),
            timerEvents = messages.eventMesgs.mapNotNull(::toTimerEvent),
        )
    }

    private fun toRawRecord(record: RecordMesg): RawRecord? {
        val timestamp = record.timestamp?.toInstant() ?: return null
        return RawRecord(
            timestamp = timestamp,
            position = geoPoint(record.positionLat, record.positionLong),
            speed = record.double(RecordMesg.EnhancedSpeedFieldNum) ?: record.double(RecordMesg.SpeedFieldNum),
            heartRate = record.heartRate?.toInt(),
            distanceM = record.double(RecordMesg.DistanceFieldNum),
            altitudeM = record.double(RecordMesg.EnhancedAltitudeFieldNum) ?: record.double(RecordMesg.AltitudeFieldNum),
        )
    }

    /**
     * The lap end is `start_time + total_elapsed_time`: in real FR965 files the lap `timestamp` field holds the
     * activity start rather than the lap end (docs/DECISIONS.md).
     */
    private fun toRawLap(lap: LapMesg): RawLap? {
        val startTime = lap.startTime?.toInstant() ?: return null
        val elapsedSec = lap.double(LapMesg.TotalElapsedTimeFieldNum) ?: return null
        return RawLap(
            startTime = startTime,
            endTime = startTime.plus(Duration.ofMillis(Math.round(elapsedSec * 1000))),
            trigger = lap.lapTrigger?.lowerName(),
        )
    }

    private fun toTimerEvent(event: EventMesg): TimerEvent? {
        if (event.event != Event.TIMER) return null
        val type = when (event.eventType) {
            EventType.START -> TimerEventType.START
            EventType.STOP, EventType.STOP_ALL, EventType.STOP_DISABLE, EventType.STOP_DISABLE_ALL -> TimerEventType.STOP
            else -> return null
        }
        return TimerEvent(event.timestamp?.toInstant() ?: return null, type)
    }

    private fun device(fileId: FileIdMesg): String? {
        val manufacturer = fileId.manufacturer ?: return null
        val manufacturerName = Manufacturer.getStringFromValue(manufacturer).ifEmpty { manufacturer.toString() }
        val product = fileId.product
        val productName = when {
            product == null -> null
            manufacturer == Manufacturer.GARMIN -> GarminProduct.getStringFromValue(product).ifEmpty { product.toString() }
            else -> product.toString()
        }
        return listOfNotNull(manufacturerName, productName).joinToString(" ").lowercase()
    }

    private fun localTzOffsetSec(activity: ActivityMesg): Int? {
        val local = activity.localTimestamp ?: return null
        val utc = activity.timestamp?.timestamp ?: return null
        return (local - utc).toInt()
    }

    private fun geoPoint(latSemicircles: Int?, lonSemicircles: Int?): GeoPoint? {
        if (latSemicircles == null || lonSemicircles == null) return null
        val lat = latSemicircles * DEGREES_PER_SEMICIRCLE
        val lon = lonSemicircles * DEGREES_PER_SEMICIRCLE
        return if (lat in -90.0..90.0 && lon in -180.0..180.0) GeoPoint(lat, lon) else null
    }

    /** Scaled field value computed in double precision; the SDK's typed getters return `Float`. */
    private fun com.garmin.fit.Mesg.double(fieldNum: Int): Double? = getFieldDoubleValue(fieldNum)

    private fun DateTime.toInstant(): Instant = FIT_EPOCH.plusSeconds(timestamp)

    private fun Enum<*>.lowerName(): String = name.lowercase()
}
