package com.ttaaa.ultimate.app.metrics

import com.ttaaa.ultimate.app.uuid
import com.ttaaa.ultimate.domain.MetricsScope
import com.ttaaa.ultimate.domain.MetricsSnapshot
import com.ttaaa.ultimate.domain.WindowMetrics
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

/** Cached metrics of sessions and segments (spec 5, 8.2). */
@Repository
class MetricsSnapshotRepository(private val jdbc: JdbcClient, private val json: JsonMapper) {

    fun save(snapshot: MetricsSnapshot) {
        jdbc.sql(
            """
            insert into metrics_snapshot (session_id, scope, scope_id, analysis_version, metrics)
            values (:sessionId, :scope, :scopeId, :analysisVersion, :metrics::jsonb)
            on conflict (scope, scope_id) do update
                set analysis_version = excluded.analysis_version, metrics = excluded.metrics
            """.trimIndent(),
        )
            .param("sessionId", snapshot.sessionId)
            .param("scope", snapshot.scope.name)
            .param("scopeId", snapshot.scopeId)
            .param("analysisVersion", snapshot.analysisVersion)
            .param("metrics", json.writeValueAsString(snapshot.metrics))
            .update()
    }

    fun find(scope: MetricsScope, scopeId: UUID): MetricsSnapshot? =
        jdbc.sql("select * from metrics_snapshot where scope = :scope and scope_id = :scopeId")
            .param("scope", scope.name)
            .param("scopeId", scopeId)
            .query { rs, _ ->
                MetricsSnapshot(
                    sessionId = rs.uuid("session_id"),
                    scope = MetricsScope.valueOf(rs.getString("scope")),
                    scopeId = rs.uuid("scope_id"),
                    analysisVersion = rs.getInt("analysis_version"),
                    metrics = json.readValue(rs.getString("metrics"), WindowMetrics::class.java),
                )
            }
            .optional()
            .orElse(null)

    fun delete(scope: MetricsScope, scopeId: UUID) {
        jdbc.sql("delete from metrics_snapshot where scope = :scope and scope_id = :scopeId")
            .param("scope", scope.name)
            .param("scopeId", scopeId)
            .update()
    }

    fun countBySession(sessionId: UUID): Int =
        jdbc.sql("select count(*) from metrics_snapshot where session_id = :sessionId")
            .param("sessionId", sessionId)
            .query(Int::class.java)
            .single()

    fun deleteBySession(sessionId: UUID) {
        jdbc.sql("delete from metrics_snapshot where session_id = :sessionId").param("sessionId", sessionId).update()
    }
}
