package com.ttaaa.ultimate.fit

import com.garmin.fit.BufferEncoder
import com.garmin.fit.DateTime
import com.garmin.fit.FileIdMesg
import com.garmin.fit.Fit
import com.garmin.fit.GarminProduct
import com.garmin.fit.Manufacturer
import com.garmin.fit.Mesg
import com.garmin.fit.RecordMesg
import com.garmin.fit.SessionMesg
import com.garmin.fit.Sport
import com.garmin.fit.SubSport
import java.time.Instant
import com.garmin.fit.File as FitFileType

/** Builds synthetic FIT files with the FIT SDK encoder for edge cases the golden files do not cover. */
object FitFiles {

    val START: Instant = Instant.parse("2026-09-28T16:02:11Z")

    fun fitFile(vararg messages: Mesg, fileType: FitFileType = FitFileType.ACTIVITY): ByteArray {
        val encoder = BufferEncoder(Fit.ProtocolVersion.V2_0)
        encoder.write(
            FileIdMesg().apply {
                type = fileType
                manufacturer = Manufacturer.GARMIN
                product = GarminProduct.FR965
                timeCreated = at(0)
            },
        )
        messages.forEach(encoder::write)
        return encoder.close()
    }

    fun at(secondsFromStart: Long): DateTime = DateTime(START.plusSeconds(secondsFromStart))

    fun semicircles(degrees: Double): Int = Math.round(degrees * (1L shl 31) / 180.0).toInt()

    fun session(sport: Sport = Sport.DISC_GOLF, subSport: SubSport = SubSport.ULTIMATE) = SessionMesg().apply {
        timestamp = at(0)
        startTime = at(0)
        this.sport = sport
        this.subSport = subSport
        totalElapsedTime = 10f
        totalTimerTime = 9f
        totalDistance = 25f
    }

    fun record(secondsFromStart: Long, enhancedSpeed: Float? = 3.5f) = RecordMesg().apply {
        timestamp = at(secondsFromStart)
        enhancedSpeed?.let { this.enhancedSpeed = it }
    }
}
