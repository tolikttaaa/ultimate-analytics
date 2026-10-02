package com.ttaaa.ultimate.app.session

import com.ttaaa.ultimate.app.doubleOrNull
import com.ttaaa.ultimate.app.floatOrNull
import com.ttaaa.ultimate.app.intOrNull
import com.ttaaa.ultimate.domain.GeoPoint
import com.ttaaa.ultimate.domain.Sample
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.PreparedStatement
import java.sql.Types
import java.time.Instant
import java.util.UUID

/**
 * The 1 Hz series of a session. Speeds, acceleration, distance and altitude are 4-byte floats in the database
 * (spec 8.1); [asStored] gives the values exactly as they come back.
 */
@Repository
class SampleRepository(private val jdbcTemplate: JdbcTemplate, private val jdbc: JdbcClient) {

    fun insertAll(sessionId: UUID, samples: List<Sample>) {
        jdbcTemplate.batchUpdate(
            """
            insert into sample (session_id, t, lat, lon, speed_raw, speed, accel, hr, distance_m, altitude_m,
                interpolated, in_pause)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            samples,
            BATCH_SIZE,
        ) { ps, sample ->
            ps.setObject(1, sessionId)
            ps.setInt(2, sample.t)
            ps.setNullableDouble(3, sample.position?.lat)
            ps.setNullableDouble(4, sample.position?.lon)
            ps.setNullableFloat(5, sample.speedRaw)
            ps.setFloat(6, sample.speed.toFloat())
            ps.setFloat(7, sample.accel.toFloat())
            if (sample.hr == null) ps.setNull(8, Types.SMALLINT) else ps.setShort(8, sample.hr!!.toShort())
            ps.setNullableFloat(9, sample.distanceM)
            ps.setNullableFloat(10, sample.altitudeM)
            ps.setBoolean(11, sample.interpolated)
            ps.setBoolean(12, sample.inPause)
        }
    }

    /** The samples of a session ordered by `t`; [startTime] is the session's `t = 0`. */
    fun findBySession(sessionId: UUID, startTime: Instant): List<Sample> =
        jdbc.sql("select * from sample where session_id = :sessionId order by t")
            .param("sessionId", sessionId)
            .query { rs, _ ->
                val t = rs.getInt("t")
                val lat = rs.doubleOrNull("lat")
                val lon = rs.doubleOrNull("lon")
                Sample(
                    t = t,
                    timestamp = startTime.plusSeconds(t.toLong()),
                    position = if (lat != null && lon != null) GeoPoint(lat, lon) else null,
                    speedRaw = rs.floatOrNull("speed_raw"),
                    speed = rs.getFloat("speed").toDouble(),
                    accel = rs.getFloat("accel").toDouble(),
                    hr = rs.intOrNull("hr"),
                    distanceM = rs.floatOrNull("distance_m"),
                    altitudeM = rs.floatOrNull("altitude_m"),
                    interpolated = rs.getBoolean("interpolated"),
                    inPause = rs.getBoolean("in_pause"),
                )
            }
            .list()

    fun countBySession(sessionId: UUID): Int =
        jdbc.sql("select count(*) from sample where session_id = :sessionId")
            .param("sessionId", sessionId)
            .query(Int::class.java)
            .single()

    private fun PreparedStatement.setNullableDouble(index: Int, value: Double?) =
        if (value == null) setNull(index, Types.DOUBLE) else setDouble(index, value)

    private fun PreparedStatement.setNullableFloat(index: Int, value: Double?) =
        if (value == null) setNull(index, Types.REAL) else setFloat(index, value.toFloat())

    private companion object {
        const val BATCH_SIZE = 1000
    }
}

/** The sample with the precision of the `sample` table: speeds, acceleration, distance and altitude as floats. */
fun Sample.asStored(): Sample = copy(
    speedRaw = speedRaw?.toFloat()?.toDouble(),
    speed = speed.toFloat().toDouble(),
    accel = accel.toFloat().toDouble(),
    distanceM = distanceM?.toFloat()?.toDouble(),
    altitudeM = altitudeM?.toFloat()?.toDouble(),
)
