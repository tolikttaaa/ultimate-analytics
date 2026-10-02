package com.ttaaa.ultimate.analysis.geo

import com.ttaaa.ultimate.domain.GeoPoint
import com.ttaaa.ultimate.domain.Geozone
import com.ttaaa.ultimate.domain.Session
import com.ttaaa.ultimate.domain.Surface
import com.ttaaa.ultimate.domain.SurfaceSource

/**
 * The geozone of a session's reference point (spec 6.6): of all geozones containing it, the smallest by area
 * (ties broken by id, so the result does not depend on the order of [geozones]). Null without a point or a match.
 */
fun matchGeozone(point: GeoPoint?, geozones: List<Geozone>): Geozone? {
    if (point == null) return null
    return geozones.filter { it.shape.contains(point) }
        .minWithOrNull(compareBy<Geozone> { it.shape.areaM2() }.thenBy { it.id })
}

/**
 * The session with the surface of [match] (spec 6.6): the geozone's surface, or UNKNOWN / NONE without a match.
 * A MANUAL surface is never overwritten (spec 5, invariant 4).
 */
fun applyGeozoneMatch(session: Session, match: Geozone?): Session = when {
    session.surfaceSource == SurfaceSource.MANUAL -> session
    match != null -> session.copy(geozoneId = match.id, surface = match.surface, surfaceSource = SurfaceSource.GEOZONE)
    else -> session.copy(geozoneId = null, surface = Surface.UNKNOWN, surfaceSource = SurfaceSource.NONE)
}
