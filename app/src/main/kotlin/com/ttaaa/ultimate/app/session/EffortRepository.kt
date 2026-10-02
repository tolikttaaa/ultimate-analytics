package com.ttaaa.ultimate.app.session

import com.ttaaa.ultimate.app.uuid
import com.ttaaa.ultimate.app.uuidOrNull
import com.ttaaa.ultimate.domain.Effort
import com.ttaaa.ultimate.domain.EffortMetrics
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import tools.jackson.databind.json.JsonMapper
import java.sql.Types
import java.util.UUID

/** An effort as stored: its identity and the segment containing its start (spec 5 invariant 3, spec 8.2). */
data class StoredEffort(val id: UUID, val sessionId: UUID, val segmentId: UUID?, val effort: Effort)

@Repository
class EffortRepository(private val jdbc: JdbcClient, private val json: JsonMapper) {

    fun insertAll(efforts: List<StoredEffort>) {
        efforts.forEach { stored ->
            jdbc.sql(
                """
                insert into effort (id, session_id, start_t, peak_t, end_t, segment_id, metrics)
                values (:id, :sessionId, :startT, :peakT, :endT, :segmentId, :metrics::jsonb)
                """.trimIndent(),
            )
                .param("id", stored.id)
                .param("sessionId", stored.sessionId)
                .param("startT", stored.effort.startT)
                .param("peakT", stored.effort.peakT)
                .param("endT", stored.effort.endT)
                .param("segmentId", stored.segmentId, Types.OTHER)
                .param("metrics", json.writeValueAsString(stored.effort.metrics))
                .update()
        }
    }

    fun updateSegment(id: UUID, segmentId: UUID?) {
        jdbc.sql("update effort set segment_id = :segmentId where id = :id")
            .param("id", id)
            .param("segmentId", segmentId, Types.OTHER)
            .update()
    }

    fun findBySession(sessionId: UUID): List<StoredEffort> =
        jdbc.sql("select * from effort where session_id = :sessionId order by start_t")
            .param("sessionId", sessionId)
            .query { rs, _ ->
                StoredEffort(
                    id = rs.uuid("id"),
                    sessionId = rs.uuid("session_id"),
                    segmentId = rs.uuidOrNull("segment_id"),
                    effort = Effort(
                        startT = rs.getInt("start_t"),
                        peakT = rs.getInt("peak_t"),
                        endT = rs.getInt("end_t"),
                        metrics = json.readValue(rs.getString("metrics"), EffortMetrics::class.java),
                    ),
                )
            }
            .list()
}
