package com.ttaaa.ultimate.analysis.geo

import com.ttaaa.ultimate.domain.GeoPoint
import com.ttaaa.ultimate.domain.GeozoneShape
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Mean Earth radius (IUGG), m. */
const val EARTH_RADIUS_M = 6_371_008.8

/** Great-circle distance between two points, m (haversine formula). */
fun haversineM(a: GeoPoint, b: GeoPoint): Double {
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLon = Math.toRadians(b.lon - a.lon)
    val h = sin(dLat / 2).let { it * it } +
        cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).let { it * it }
    return 2 * EARTH_RADIUS_M * asin(sqrt(h.coerceIn(0.0, 1.0)))
}

/**
 * Whether [shape] contains [point]: within the radius of a circle (haversine), or inside a polygon by the even-odd
 * rule. Polygon edges are straight in latitude/longitude, which is exact enough for fields and beaches; polygons must
 * not cross the antimeridian.
 */
fun GeozoneShape.contains(point: GeoPoint): Boolean = when (this) {
    is GeozoneShape.Circle -> haversineM(center, point) <= radiusM
    is GeozoneShape.Polygon -> {
        var inside = false
        for (i in ring.indices) {
            val a = ring[i]
            val b = ring[(i + 1) % ring.size]
            val crosses = (a.lat > point.lat) != (b.lat > point.lat) &&
                point.lon < a.lon + (point.lat - a.lat) / (b.lat - a.lat) * (b.lon - a.lon)
            if (crosses) inside = !inside
        }
        inside
    }
}

/**
 * Area of the shape, m². Polygons are projected onto a local plane (equirectangular, scaled at the vertices' mean
 * latitude, so the vertex order does not matter), which is accurate for areas of a few kilometres.
 */
fun GeozoneShape.areaM2(): Double = when (this) {
    is GeozoneShape.Circle -> PI * radiusM * radiusM
    is GeozoneShape.Polygon -> {
        val origin = ring.first()
        val metresPerDegree = EARTH_RADIUS_M * PI / 180
        val lonScale = cos(Math.toRadians(ring.map { it.lat }.average()))
        val xs = ring.map { (it.lon - origin.lon) * metresPerDegree * lonScale }
        val ys = ring.map { (it.lat - origin.lat) * metresPerDegree }
        val twiceArea = ring.indices.sumOf { i ->
            val j = (i + 1) % ring.size
            xs[i] * ys[j] - xs[j] * ys[i]
        }
        abs(twiceArea) / 2
    }
}
