package com.ttaaa.ultimate.app.segment

import com.ttaaa.ultimate.app.session.SessionFilter
import com.ttaaa.ultimate.app.toUtc
import com.ttaaa.ultimate.app.uuid
import com.ttaaa.ultimate.app.uuidOrNull
import com.ttaaa.ultimate.domain.Segment
import com.ttaaa.ultimate.domain.SegmentSource
import com.ttaaa.ultimate.domain.TimeRange
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Types
import java.util.UUID

@Repository
class SegmentRepository(private val jdbc: JdbcClient) {

    fun insertAll(segments: List<Segment>) {
        segments.forEach { segment ->
            jdbc.sql(
                """
                insert into segment (id, session_id, start_t, end_t, drill_type_id, label, source)
                values (:id, :sessionId, :startT, :endT, :drillTypeId, :label, :source)
                """.trimIndent(),
            )
                .param("id", segment.id)
                .param("sessionId", segment.sessionId)
                .param("startT", segment.range.fromT)
                .param("endT", segment.range.toT)
                .param("drillTypeId", segment.drillTypeId, Types.OTHER)
                .param("label", segment.label, Types.VARCHAR)
                .param("source", segment.source.name)
                .update()
        }
    }

    fun update(segment: Segment) {
        jdbc.sql(
            """
            update segment set start_t = :startT, end_t = :endT, drill_type_id = :drillTypeId, label = :label,
                source = :source
            where id = :id
            """.trimIndent(),
        )
            .param("id", segment.id)
            .param("startT", segment.range.fromT)
            .param("endT", segment.range.toT)
            .param("drillTypeId", segment.drillTypeId, Types.OTHER)
            .param("label", segment.label, Types.VARCHAR)
            .param("source", segment.source.name)
            .update()
    }

    fun delete(id: UUID) {
        jdbc.sql("delete from segment where id = :id").param("id", id).update()
    }

    fun drillTypeExists(id: UUID): Boolean =
        jdbc.sql("select exists(select 1 from drill_type where id = :id)").param("id", id).query(Boolean::class.java).single()

    /** Segments of a drill type in the sessions matching [filter], in time order. */
    fun findByDrillType(drillTypeId: UUID, filter: SessionFilter): List<Segment> {
        val conditions = listOfNotNull(
            "g.drill_type_id = :drillTypeId",
            filter.from?.let { "s.start_time >= :from" },
            filter.to?.let { "s.start_time < :to" },
            filter.surface?.let { "s.surface = :surface" },
        )
        var statement = jdbc.sql(
            "select g.* from segment g join session s on s.id = g.session_id where " +
                conditions.joinToString(" and ") + " order by s.start_time, g.start_t",
        ).param("drillTypeId", drillTypeId)
        filter.from?.let { statement = statement.param("from", it.toUtc()) }
        filter.to?.let { statement = statement.param("to", it.toUtc()) }
        filter.surface?.let { statement = statement.param("surface", it.name) }
        return statement.query(::map).list()
    }

    fun findBySession(sessionId: UUID): List<Segment> =
        jdbc.sql("select * from segment where session_id = :sessionId order by start_t")
            .param("sessionId", sessionId)
            .query(::map)
            .list()

    private fun map(rs: ResultSet, @Suppress("UNUSED_PARAMETER") rowNum: Int) = Segment(
        id = rs.uuid("id"),
        sessionId = rs.uuid("session_id"),
        range = TimeRange(rs.getInt("start_t"), rs.getInt("end_t")),
        drillTypeId = rs.uuidOrNull("drill_type_id"),
        label = rs.getString("label"),
        source = SegmentSource.valueOf(rs.getString("source")),
    )
}
