package com.ttaaa.ultimate.app.segment

import com.ttaaa.ultimate.app.uuid
import com.ttaaa.ultimate.app.uuidOrNull
import com.ttaaa.ultimate.domain.Segment
import com.ttaaa.ultimate.domain.SegmentSource
import com.ttaaa.ultimate.domain.TimeRange
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
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

    fun findBySession(sessionId: UUID): List<Segment> =
        jdbc.sql("select * from segment where session_id = :sessionId order by start_t")
            .param("sessionId", sessionId)
            .query { rs, _ ->
                Segment(
                    id = rs.uuid("id"),
                    sessionId = rs.uuid("session_id"),
                    range = TimeRange(rs.getInt("start_t"), rs.getInt("end_t")),
                    drillTypeId = rs.uuidOrNull("drill_type_id"),
                    label = rs.getString("label"),
                    source = SegmentSource.valueOf(rs.getString("source")),
                )
            }
            .list()
}
