package com.ttaaa.ultimate.app.drilltype

import com.ttaaa.ultimate.app.uuid
import com.ttaaa.ultimate.domain.DrillKind
import com.ttaaa.ultimate.domain.DrillType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.util.UUID

@Repository
class DrillTypeRepository(private val jdbc: JdbcClient) {

    fun insert(drillType: DrillType) {
        jdbc.sql("insert into drill_type (id, code, name, kind, color) values (:id, :code, :name, :kind, :color)")
            .params(columns(drillType))
            .update()
    }

    fun update(drillType: DrillType) {
        jdbc.sql("update drill_type set code = :code, name = :name, kind = :kind, color = :color where id = :id")
            .params(columns(drillType))
            .update()
    }

    fun delete(id: UUID): Boolean = jdbc.sql("delete from drill_type where id = :id").param("id", id).update() > 0

    fun findAll(): List<DrillType> = jdbc.sql("select * from drill_type order by name, code").query(::map).list()

    fun findById(id: UUID): DrillType? =
        jdbc.sql("select * from drill_type where id = :id").param("id", id).query(::map).optional().orElse(null)

    private fun columns(drillType: DrillType) = mapOf(
        "id" to drillType.id,
        "code" to drillType.code,
        "name" to drillType.name,
        "kind" to drillType.kind.name,
        "color" to drillType.color,
    )

    private fun map(rs: ResultSet, @Suppress("UNUSED_PARAMETER") rowNum: Int) = DrillType(
        id = rs.uuid("id"),
        code = rs.getString("code"),
        name = rs.getString("name"),
        color = rs.getString("color"),
        kind = DrillKind.valueOf(rs.getString("kind")),
    )
}
