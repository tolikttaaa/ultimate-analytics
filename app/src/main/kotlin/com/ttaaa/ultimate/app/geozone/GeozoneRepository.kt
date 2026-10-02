package com.ttaaa.ultimate.app.geozone

import com.fasterxml.jackson.annotation.JsonInclude
import com.ttaaa.ultimate.app.uuid
import com.ttaaa.ultimate.domain.GeoPoint
import com.ttaaa.ultimate.domain.Geozone
import com.ttaaa.ultimate.domain.GeozoneShape
import com.ttaaa.ultimate.domain.Surface
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

@Repository
class GeozoneRepository(private val jdbc: JdbcClient, private val json: JsonMapper) {

    fun insert(geozone: Geozone) {
        jdbc.sql("insert into geozone (id, name, surface, shape) values (:id, :name, :surface, :shape::jsonb)")
            .param("id", geozone.id)
            .param("name", geozone.name)
            .param("surface", geozone.surface.name)
            .param("shape", json.writeValueAsString(GeozoneShapeJson.of(geozone.shape)))
            .update()
    }

    fun findAll(): List<Geozone> =
        jdbc.sql("select * from geozone order by name, id")
            .query { rs, _ ->
                Geozone(
                    id = rs.uuid("id"),
                    name = rs.getString("name"),
                    surface = Surface.valueOf(rs.getString("surface")),
                    shape = json.readValue(rs.getString("shape"), GeozoneShapeJson::class.java).toShape(),
                )
            }
            .list()

    fun findById(id: UUID): Geozone? = findAll().find { it.id == id } // a handful of geozones

    fun update(geozone: Geozone) {
        jdbc.sql("update geozone set name = :name, surface = :surface, shape = :shape::jsonb where id = :id")
            .param("id", geozone.id)
            .param("name", geozone.name)
            .param("surface", geozone.surface.name)
            .param("shape", json.writeValueAsString(GeozoneShapeJson.of(geozone.shape)))
            .update()
    }

    fun delete(id: UUID): Boolean = jdbc.sql("delete from geozone where id = :id").param("id", id).update() > 0
}

/**
 * A geozone shape as JSON, in the `geozone.shape` column and in the API (spec 8.1): a circle
 * `{"type":"circle","lat":..,"lon":..,"radiusM":..}` or a GeoJSON Polygon with one closed ring of `[lon, lat]` pairs.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class GeozoneShapeJson(
    /** `circle` or `Polygon`. */
    val type: String,
    val lat: Double? = null,
    val lon: Double? = null,
    val radiusM: Double? = null,
    /** GeoJSON Polygon coordinates: one ring of `[lon, lat]` positions, the first repeated at the end. */
    val coordinates: List<List<List<Double>>>? = null,
) {
    fun toShape(): GeozoneShape = when (type) {
        CIRCLE -> {
            require(lat != null && lon != null && radiusM != null) { "A circle needs lat, lon and radiusM" }
            GeozoneShape.Circle(GeoPoint(lat, lon), radiusM)
        }
        POLYGON -> {
            require(coordinates?.size == 1) { "A polygon needs exactly one ring (holes are not supported)" }
            val positions = coordinates.single()
            require(positions.all { it.size >= 2 }) { "Polygon positions are [lon, lat] pairs" }
            val points = positions.map { (lon, lat) -> GeoPoint(lat, lon) }
            GeozoneShape.Polygon(if (points.size > 1 && points.first() == points.last()) points.dropLast(1) else points)
        }
        else -> throw IllegalArgumentException("Unknown shape type '$type'; expected '$CIRCLE' or '$POLYGON'")
    }

    companion object {
        const val CIRCLE = "circle"
        const val POLYGON = "Polygon"

        fun of(shape: GeozoneShape): GeozoneShapeJson = when (shape) {
            is GeozoneShape.Circle -> GeozoneShapeJson(CIRCLE, shape.center.lat, shape.center.lon, shape.radiusM)
            is GeozoneShape.Polygon -> GeozoneShapeJson(
                POLYGON,
                coordinates = listOf((shape.ring + shape.ring.first()).map { listOf(it.lon, it.lat) }),
            )
        }
    }
}
