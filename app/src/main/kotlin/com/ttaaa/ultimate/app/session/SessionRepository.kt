package com.ttaaa.ultimate.app.session

import com.ttaaa.ultimate.app.doubleOrNull
import com.ttaaa.ultimate.app.instant
import com.ttaaa.ultimate.app.intOrNull
import com.ttaaa.ultimate.app.toUtc
import com.ttaaa.ultimate.app.uuid
import com.ttaaa.ultimate.app.uuidOrNull
import com.ttaaa.ultimate.domain.GeoPoint
import com.ttaaa.ultimate.domain.RecordingMode
import com.ttaaa.ultimate.domain.Session
import com.ttaaa.ultimate.domain.Surface
import com.ttaaa.ultimate.domain.SurfaceSource
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Types
import java.util.UUID

@Repository
class SessionRepository(private val jdbc: JdbcClient) {

    fun insert(session: Session) {
        jdbc.sql(
            """
            insert into session (id, file_sha256, file_name, uploaded_at, start_time, local_tz_offset_sec, elapsed_sec,
                timer_sec, distance_m, device, sport, sub_sport, start_lat, start_lon, geozone_id, surface,
                surface_source, notes, analysis_version, recording_mode)
            values (:id, :fileSha256, :fileName, :uploadedAt, :startTime, :localTzOffsetSec, :elapsedSec,
                :timerSec, :distanceM, :device, :sport, :subSport, :startLat, :startLon, :geozoneId, :surface,
                :surfaceSource, :notes, :analysisVersion, :recordingMode)
            """.trimIndent(),
        )
            .param("id", session.id)
            .param("fileSha256", session.fileSha256)
            .param("fileName", session.fileName)
            .param("uploadedAt", session.uploadedAt.toUtc())
            .param("startTime", session.startTime.toUtc())
            .param("localTzOffsetSec", session.localTzOffsetSec, Types.INTEGER)
            .param("elapsedSec", session.elapsedSec)
            .param("timerSec", session.timerSec)
            .param("distanceM", session.distanceM, Types.DOUBLE)
            .param("device", session.device, Types.VARCHAR)
            .param("sport", session.sport, Types.VARCHAR)
            .param("subSport", session.subSport, Types.VARCHAR)
            .param("startLat", session.startPosition?.lat, Types.DOUBLE)
            .param("startLon", session.startPosition?.lon, Types.DOUBLE)
            .param("geozoneId", session.geozoneId, Types.OTHER)
            .param("surface", session.surface.name)
            .param("surfaceSource", session.surfaceSource.name)
            .param("notes", session.notes, Types.VARCHAR)
            .param("analysisVersion", session.analysisVersion)
            .param("recordingMode", session.recordingMode.name)
            .update()
    }

    fun findById(id: UUID): Session? =
        jdbc.sql("select * from session where id = :id").param("id", id).query(::mapSession).optional().orElse(null)

    fun findIdBySha256(sha256: String): UUID? =
        jdbc.sql("select id from session where file_sha256 = :sha256")
            .param("sha256", sha256)
            .query(UUID::class.java)
            .optional()
            .orElse(null)

    fun count(): Long = jdbc.sql("select count(*) from session").query(Long::class.java).single()

    private fun mapSession(rs: ResultSet, @Suppress("UNUSED_PARAMETER") rowNum: Int): Session {
        val startLat = rs.doubleOrNull("start_lat")
        val startLon = rs.doubleOrNull("start_lon")
        return Session(
            id = rs.uuid("id"),
            fileSha256 = rs.getString("file_sha256"),
            fileName = rs.getString("file_name"),
            uploadedAt = rs.instant("uploaded_at"),
            startTime = rs.instant("start_time"),
            localTzOffsetSec = rs.intOrNull("local_tz_offset_sec"),
            elapsedSec = rs.getInt("elapsed_sec"),
            timerSec = rs.getInt("timer_sec"),
            distanceM = rs.doubleOrNull("distance_m"),
            device = rs.getString("device"),
            sport = rs.getString("sport"),
            subSport = rs.getString("sub_sport"),
            startPosition = if (startLat != null && startLon != null) GeoPoint(startLat, startLon) else null,
            geozoneId = rs.uuidOrNull("geozone_id"),
            surface = Surface.valueOf(rs.getString("surface")),
            surfaceSource = SurfaceSource.valueOf(rs.getString("surface_source")),
            notes = rs.getString("notes"),
            analysisVersion = rs.getInt("analysis_version"),
            recordingMode = RecordingMode.valueOf(rs.getString("recording_mode")),
        )
    }
}
