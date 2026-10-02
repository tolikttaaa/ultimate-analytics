package com.ttaaa.ultimate.app.session

import com.ttaaa.ultimate.app.NotFoundException
import com.ttaaa.ultimate.app.storage.RawFileStore
import com.ttaaa.ultimate.domain.Session
import com.ttaaa.ultimate.domain.Surface
import com.ttaaa.ultimate.domain.SurfaceSource
import org.springframework.stereotype.Service
import java.util.Optional
import java.util.UUID

@Service
class SessionService(private val sessions: SessionRepository, private val rawFiles: RawFileStore) {

    fun get(id: UUID): Session = sessions.findById(id) ?: throw NotFoundException("Session $id not found")

    fun list(filter: SessionFilter, page: Int, size: Int): Pair<List<Session>, Long> {
        require(page >= 0) { "page must not be negative" }
        require(size in 1..MAX_PAGE_SIZE) { "size must be between 1 and $MAX_PAGE_SIZE" }
        return sessions.findPage(filter, page * size, size) to sessions.count(filter)
    }

    /**
     * Sets the surface and/or the notes (spec 9.1). A surface set by the user is MANUAL and is never overwritten by
     * geozone matching (spec 5, invariant 4). [notes]: null leaves them, empty or blank clears them.
     */
    fun update(id: UUID, surface: Surface?, notes: Optional<String>?): Session {
        val session = get(id)
        val updated = session.copy(
            surface = surface ?: session.surface,
            surfaceSource = if (surface != null) SurfaceSource.MANUAL else session.surfaceSource,
            notes = if (notes != null) notes.orElse(null)?.takeIf { it.isNotBlank() } else session.notes,
        )
        sessions.update(updated)
        return updated
    }

    /** Deletes the session with everything it owns, then its raw file (spec 9.1). */
    fun delete(id: UUID) {
        val session = get(id)
        sessions.delete(id)
        rawFiles.delete(session.fileSha256)
    }

    fun rawFile(id: UUID): Pair<Session, ByteArray> {
        val session = get(id)
        if (!rawFiles.exists(session.fileSha256)) throw NotFoundException("Raw file of session $id not found")
        return session to rawFiles.read(session.fileSha256)
    }

    companion object {
        const val MAX_PAGE_SIZE = 100
    }
}
