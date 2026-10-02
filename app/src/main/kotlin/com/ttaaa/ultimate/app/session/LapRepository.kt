package com.ttaaa.ultimate.app.session

import com.ttaaa.ultimate.domain.Lap
import com.ttaaa.ultimate.domain.TimeRange
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Types
import java.util.UUID

@Repository
class LapRepository(private val jdbc: JdbcClient) {

    fun insertAll(sessionId: UUID, laps: List<Lap>) {
        laps.forEach { lap ->
            jdbc.sql("insert into lap (session_id, idx, start_t, end_t, trigger) values (:sessionId, :idx, :startT, :endT, :trigger)")
                .param("sessionId", sessionId)
                .param("idx", lap.index)
                .param("startT", lap.range.fromT)
                .param("endT", lap.range.toT)
                .param("trigger", lap.trigger, Types.VARCHAR)
                .update()
        }
    }

    fun findBySession(sessionId: UUID): List<Lap> =
        jdbc.sql("select * from lap where session_id = :sessionId order by idx")
            .param("sessionId", sessionId)
            .query { rs, _ -> Lap(rs.getInt("idx"), TimeRange(rs.getInt("start_t"), rs.getInt("end_t")), rs.getString("trigger")) }
            .list()
}
