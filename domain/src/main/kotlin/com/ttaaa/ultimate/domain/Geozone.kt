package com.ttaaa.ultimate.domain

import java.util.UUID

/** A saved area with a name and a surface, used to classify sessions (spec 3, 6.6). */
data class Geozone(
    val id: UUID,
    val name: String,
    /** [Surface.GRASS] or [Surface.SAND]. */
    val surface: Surface,
    val shape: GeozoneShape,
) {
    init {
        require(name.isNotBlank()) { "Geozone name must not be blank" }
        require(surface != Surface.UNKNOWN) { "A geozone surface is GRASS or SAND" }
    }
}

sealed interface GeozoneShape {

    data class Circle(val center: GeoPoint, val radiusM: Double) : GeozoneShape {
        init {
            require(radiusM > 0.0 && radiusM.isFinite()) { "Circle radius must be positive: $radiusM" }
        }
    }

    /** A simple polygon. [ring] lists each vertex once: the first vertex is not repeated at the end. */
    data class Polygon(val ring: List<GeoPoint>) : GeozoneShape {
        init {
            require(ring.size >= 3) { "A polygon needs at least 3 vertices, got ${ring.size}" }
            require(ring.first() != ring.last()) { "Polygon ring must not repeat the first vertex at the end" }
        }
    }
}
