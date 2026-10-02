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
import java.time.Instant
import java.util.UUID

/** Filter of the sessions list (spec 9.1): start time in `[from, to)` and surface; null means any. */
data class SessionFilter(val from: Instant? = null, val to: Instant? = null, val surface: Surface? = null)

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

    /** One page of the sessions matching [filter], newest first. */
    fun findPage(filter: SessionFilter, offset: Int, limit: Int): List<Session> =
        filtered("select * from session", filter, " order by start_time desc, id limit :limit offset :offset")
            .param("limit", limit)
            .param("offset", offset)
            .query(::mapSession)
            .list()

    fun count(filter: SessionFilter): Long =
        filtered("select count(*) from session", filter, "").query(Long::class.java).single()

    fun findOutdated(analysisVersion: Int): List<Session> =
        jdbc.sql("select * from session where analysis_version < :version order by start_time")
            .param("version", analysisVersion)
            .query(::mapSession)
            .list()

    fun findAll(): List<Session> = jdbc.sql("select * from session order by start_time").query(::mapSession).list()

    /** Writes every column except the identity of the file (id, hash, name, upload time). */
    fun update(session: Session) {
        jdbc.sql(
            """
            update session set start_time = :startTime, local_tz_offset_sec = :localTzOffsetSec,
                elapsed_sec = :elapsedSec, timer_sec = :timerSec, distance_m = :distanceM, device = :device,
                sport = :sport, sub_sport = :subSport, start_lat = :startLat, start_lon = :startLon,
                geozone_id = :geozoneId, surface = :surface, surface_source = :surfaceSource, notes = :notes,
                analysis_version = :analysisVersion, recording_mode = :recordingMode
            where id = :id
            """.trimIndent(),
        )
            .param("id", session.id)
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

    /** Deletes the session and, by cascade, everything it owns; false if there was none. */
    fun delete(id: UUID): Boolean = jdbc.sql("delete from session where id = :id").param("id", id).update() > 0

    /** Locks the session row until the end of the transaction, serialising edits of its segments. */
    fun lock(id: UUID): Boolean =
        jdbc.sql("select id from session where id = :id for update").param("id", id).query(UUID::class.java).optional().isPresent

    private fun filtered(select: String, filter: SessionFilter, suffix: String): JdbcClient.StatementSpec {
        val conditions = listOfNotNull(
            filter.from?.let { "start_time >= :from" },
            filter.to?.let { "start_time < :to" },
            filter.surface?.let { "surface = :surface" },
        )
        val where = if (conditions.isEmpty()) "" else " where " + conditions.joinToString(" and ")
        var statement = jdbc.sql(select + where + suffix)
        filter.from?.let { statement = statement.param("from", it.toUtc()) }
        filter.to?.let { statement = statement.param("to", it.toUtc()) }
        filter.surface?.let { statement = statement.param("surface", it.name) }
        return statement
    }

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
