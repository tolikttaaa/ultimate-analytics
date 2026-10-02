package com.ttaaa.ultimate.app.web

import com.ttaaa.ultimate.app.geozone.GeozoneChange
import com.ttaaa.ultimate.app.geozone.GeozoneService
import com.ttaaa.ultimate.app.geozone.GeozoneShapeJson
import com.ttaaa.ultimate.domain.Geozone
import com.ttaaa.ultimate.domain.Surface
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class GeozoneDto(val id: UUID, val name: String, val surface: Surface, val shape: GeozoneShapeJson) {
    constructor(geozone: Geozone) : this(geozone.id, geozone.name, geozone.surface, GeozoneShapeJson.of(geozone.shape))
}

/** A new geozone; the surface is GRASS or SAND. */
data class GeozoneCreate(val name: String, val surface: Surface, val shape: GeozoneShapeJson)

data class GeozonePatch(val name: String? = null, val surface: Surface? = null, val shape: GeozoneShapeJson? = null)

/** The geozone after the change (none after a delete) and the number of sessions whose surface or geozone changed. */
data class GeozoneChangeDto(val geozone: GeozoneDto?, val affectedSessionCount: Int) {
    constructor(change: GeozoneChange) : this(change.geozone?.let(::GeozoneDto), change.affectedSessionCount)
}

@RestController
@RequestMapping("/api/geozones")
@Tag(name = "Geozones")
class GeozoneController(private val geozones: GeozoneService) {

    @GetMapping
    fun list(): List<GeozoneDto> = geozones.list().map(::GeozoneDto)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a geozone and match all sessions again (except manual surfaces)")
    fun create(@RequestBody body: GeozoneCreate): GeozoneChangeDto =
        GeozoneChangeDto(geozones.create(body.name, body.surface, body.shape.toShape()))

    @PatchMapping("/{id}")
    @Operation(summary = "Edit a geozone and match all sessions again; returns the number of affected sessions")
    fun update(@PathVariable id: UUID, @RequestBody body: GeozonePatch): GeozoneChangeDto =
        GeozoneChangeDto(geozones.update(id, body.name, body.surface, body.shape?.toShape()))

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a geozone and match its sessions again; returns the number of affected sessions")
    fun delete(@PathVariable id: UUID): GeozoneChangeDto = GeozoneChangeDto(geozones.delete(id))
}
