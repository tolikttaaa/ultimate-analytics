package com.ttaaa.ultimate.app.geozone

import com.ttaaa.ultimate.analysis.geo.applyGeozoneMatch
import com.ttaaa.ultimate.analysis.geo.matchGeozone
import com.ttaaa.ultimate.app.NotFoundException
import com.ttaaa.ultimate.app.session.SessionRepository
import com.ttaaa.ultimate.domain.Geozone
import com.ttaaa.ultimate.domain.GeozoneShape
import com.ttaaa.ultimate.domain.Surface
import com.ttaaa.ultimate.domain.SurfaceSource
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** A geozone after a change, and how many sessions changed their geozone or surface because of it. */
data class GeozoneChange(val geozone: Geozone?, val affectedSessionCount: Int)

/**
 * Geozones and the surface classification of sessions (spec 6.6): every change re-runs the matching for all sessions
 * whose surface is not MANUAL.
 */
@Service
class GeozoneService(private val geozones: GeozoneRepository, private val sessions: SessionRepository) {

    fun list(): List<Geozone> = geozones.findAll()

    @Transactional
    fun create(name: String, surface: Surface, shape: GeozoneShape): GeozoneChange {
        val geozone = Geozone(UUID.randomUUID(), name.trim(), surface, shape)
        geozones.insert(geozone)
        return GeozoneChange(geozone, rematchSessions())
    }

    @Transactional
    fun update(id: UUID, name: String?, surface: Surface?, shape: GeozoneShape?): GeozoneChange {
        val current = get(id)
        val updated = current.copy(name = name?.trim() ?: current.name, surface = surface ?: current.surface, shape = shape ?: current.shape)
        geozones.update(updated)
        return GeozoneChange(updated, rematchSessions())
    }

    /** Deletes the geozone; its sessions are matched again (another geozone, or an unknown surface). */
    @Transactional
    fun delete(id: UUID): GeozoneChange {
        if (!geozones.delete(id)) throw NotFoundException("Geozone $id not found")
        return GeozoneChange(null, rematchSessions())
    }

    private fun get(id: UUID): Geozone = geozones.findById(id) ?: throw NotFoundException("Geozone $id not found")

    /** Matches every session whose surface is not MANUAL again; returns the number of sessions that changed. */
    private fun rematchSessions(): Int {
        val all = geozones.findAll()
        return sessions.findAll().filter { it.surfaceSource != SurfaceSource.MANUAL }.count { session ->
            val matched = applyGeozoneMatch(session, matchGeozone(session.startPosition, all))
            if (matched != session) sessions.update(matched)
            matched != session
        }
    }
}
