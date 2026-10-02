package com.ttaaa.ultimate.fit

import com.garmin.fit.ActivityMesg
import com.garmin.fit.DateTime
import com.garmin.fit.Event
import com.garmin.fit.EventMesg
import com.garmin.fit.EventType
import com.garmin.fit.LapMesg
import com.garmin.fit.LapTrigger
import com.garmin.fit.RecordMesg
import com.garmin.fit.Sport
import com.garmin.fit.SubSport
import com.ttaaa.ultimate.domain.RawLap
import com.ttaaa.ultimate.domain.TimerEventType
import com.ttaaa.ultimate.fit.FitFiles.START
import com.ttaaa.ultimate.fit.FitFiles.at
import com.ttaaa.ultimate.fit.FitFiles.fitFile
import com.ttaaa.ultimate.fit.FitFiles.record
import com.ttaaa.ultimate.fit.FitFiles.semicircles
import com.ttaaa.ultimate.fit.FitFiles.session
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.time.Instant
import com.garmin.fit.File as FitFileType

class FitParserTest {

    private fun parse(bytes: ByteArray) = FitParser.parse(bytes.inputStream())

    @Test
    fun `reads the session header and the device`() {
        val raw = parse(fitFile(session(), record(0)))

        raw.device shouldBe "garmin fr965"
        raw.sport shouldBe "disc_golf"
        raw.subSport shouldBe "ultimate"
        raw.startTime shouldBe START
        raw.totalElapsedSec shouldBe 10.0
        raw.totalTimerSec shouldBe 9.0
        raw.totalDistanceM shouldBe 25.0
    }

    @Test
    fun `converts semicircles to degrees and FIT timestamps to UTC`() {
        val positioned = record(0).apply {
            positionLat = semicircles(34.6812)
            positionLong = semicircles(-33.0421)
        }
        val raw = parse(fitFile(session(), positioned, RecordMesg().apply { timestamp = DateTime(1_000_000_000L) }))

        val position = raw.records[0].position.shouldNotBeNull()
        position.lat shouldBe (34.6812 plusOrMinus 1e-6)
        position.lon shouldBe (-33.0421 plusOrMinus 1e-6)
        // FIT epoch 1989-12-31T00:00:00Z + 10^9 s
        raw.records[1].timestamp shouldBe Instant.parse("2021-09-08T01:46:40Z")
    }

    @Test
    fun `reads speed, heart rate, distance and altitude`() {
        val full = record(0, enhancedSpeed = 4.25f).apply {
            heartRate = 151
            distance = 12.5f
            enhancedAltitude = 21.4f
        }
        val plainFields = record(1, enhancedSpeed = null).apply {
            speed = 2.5f
            altitude = 18.0f
        }
        val empty = record(2, enhancedSpeed = null)

        val (first, second, third) = parse(fitFile(session(), full, plainFields, empty)).records

        first.speed shouldBe 4.25
        first.heartRate shouldBe 151
        first.distanceM shouldBe 12.5
        first.altitudeM shouldBe (21.4 plusOrMinus 1e-9)
        second.speed shouldBe 2.5
        second.altitudeM shouldBe (18.0 plusOrMinus 1e-9)
        third.speed shouldBe null
        third.position shouldBe null
        third.heartRate shouldBe null
    }

    @Test
    fun `drops positions outside the valid range`() {
        val invalid = record(0).apply {
            positionLat = semicircles(95.0)
            positionLong = semicircles(33.0)
        }

        parse(fitFile(session(), invalid)).records[0].position shouldBe null
    }

    @Test
    fun `lap end is start plus elapsed time, not the timestamp field`() {
        // Real FR965 files write the activity start into the lap timestamp field.
        val lap = LapMesg().apply {
            timestamp = at(0)
            startTime = at(100)
            totalElapsedTime = 60.5f
            lapTrigger = LapTrigger.MANUAL
        }

        parse(fitFile(session(), record(0), lap)).laps shouldBe listOf(
            RawLap(START.plusSeconds(100), START.plusMillis(160_500), "manual"),
        )
    }

    @Test
    fun `keeps timer starts and stops only`() {
        fun event(seconds: Long, event: Event, type: EventType) = EventMesg().apply {
            timestamp = at(seconds)
            this.event = event
            eventType = type
        }
        val raw = parse(
            fitFile(
                session(),
                record(0),
                event(0, Event.TIMER, EventType.START),
                event(19, Event.TIMER, EventType.STOP_ALL),
                event(20, Event.TIMER, EventType.START),
                event(30, Event.TIMER, EventType.STOP_DISABLE_ALL),
                event(40, Event.RECOVERY_HR, EventType.MARKER),
            ),
        )

        raw.timerEvents.map { it.type } shouldBe
            listOf(TimerEventType.START, TimerEventType.STOP, TimerEventType.START, TimerEventType.STOP)
        raw.timerEvents.map { it.timestamp } shouldBe listOf(0L, 19, 20, 30).map(START::plusSeconds)
    }

    @Test
    fun `local time offset comes from the activity message`() {
        val activity = ActivityMesg().apply {
            timestamp = at(10)
            localTimestamp = at(10).timestamp + 10_800
        }

        parse(fitFile(session(), record(0), activity)).localTzOffsetSec shouldBe 10_800
    }

    @Test
    fun `parses activities of other sports`() {
        val raw = parse(fitFile(session(Sport.RUNNING, SubSport.TRACK), record(0)))

        raw.sport shouldBe "running"
        raw.subSport shouldBe "track"
    }

    @Test
    fun `rejects files that are not activities`() {
        val error = shouldThrow<FitParseException.NotAnActivity> {
            parse(fitFile(session(), record(0), fileType = FitFileType.COURSE))
        }
        error.fileType shouldBe "course"
    }

    @Test
    fun `rejects data that is not FIT`() {
        shouldThrow<FitParseException.NotAFitFile> { parse(ByteArray(0)) }
        shouldThrow<FitParseException.NotAFitFile> { parse("not a fit file at all".toByteArray()) }
    }

    @Test
    fun `rejects a corrupted file`() {
        val bytes = Files.readAllBytes(Golden.fitFiles.first())
        bytes[bytes.size / 2] = (bytes[bytes.size / 2] + 1).toByte()

        shouldThrow<FitParseException.Corrupted> { parse(bytes) }
    }

    @Test
    fun `rejects activities without a session or records`() {
        shouldThrow<FitParseException.MissingData> { parse(fitFile(record(0))) }.what shouldBe "session"
        shouldThrow<FitParseException.MissingData> { parse(fitFile(session())) }.what shouldBe "records"
    }
}
