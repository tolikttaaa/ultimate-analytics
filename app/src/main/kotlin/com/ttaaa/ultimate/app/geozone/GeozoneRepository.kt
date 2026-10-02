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
            .param("shape", json.writeValueAsString(ShapeJson.of(geozone.shape)))
            .update()
    }

    fun findAll(): List<Geozone> =
        jdbc.sql("select * from geozone order by name, id")
            .query { rs, _ ->
                Geozone(
                    id = rs.uuid("id"),
                    name = rs.getString("name"),
                    surface = Surface.valueOf(rs.getString("surface")),
                    shape = json.readValue(rs.getString("shape"), ShapeJson::class.java).toShape(),
                )
            }
            .list()

    fun findById(id: UUID): Geozone? = findAll().find { it.id == id }
}

/**
 * The `geozone.shape` column (spec 8.1): `{"type":"circle","lat":..,"lon":..,"radiusM":..}` or a GeoJSON Polygon,
 * whose ring is closed and lists `[lon, lat]` pairs.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
internal data class ShapeJson(
    val type: String,
    val lat: Double? = null,
    val lon: Double? = null,
    val radiusM: Double? = null,
    val coordinates: List<List<List<Double>>>? = null,
) {
    fun toShape(): GeozoneShape = when (type) {
        CIRCLE -> GeozoneShape.Circle(GeoPoint(checkNotNull(lat), checkNotNull(lon)), checkNotNull(radiusM))
        POLYGON -> GeozoneShape.Polygon(checkNotNull(coordinates).first().dropLast(1).map { (lon, lat) -> GeoPoint(lat, lon) })
        else -> error("Unknown geozone shape type: $type")
    }

    companion object {
        const val CIRCLE = "circle"
        const val POLYGON = "Polygon"

        fun of(shape: GeozoneShape): ShapeJson = when (shape) {
            is GeozoneShape.Circle -> ShapeJson(CIRCLE, shape.center.lat, shape.center.lon, shape.radiusM)
            is GeozoneShape.Polygon -> ShapeJson(
                POLYGON,
                coordinates = listOf((shape.ring + shape.ring.first()).map { listOf(it.lon, it.lat) }),
            )
        }
    }
}
