package com.ttaaa.ultimate.app.ingestion

import com.ttaaa.ultimate.domain.Surface
import java.util.UUID

enum class UploadStatus { CREATED, DUPLICATE, FAILED }

/** Outcome for one uploaded FIT file (spec 7.4 step 6, 9.2). */
data class UploadResult(
    val fileName: String,
    val status: UploadStatus,
    /** The new session, or the existing one for a duplicate. */
    val sessionId: UUID? = null,
    val surface: Surface? = null,
    /** No geozone matched: the UI asks for a surface or a new geozone (spec 6.6). */
    val needsSurface: Boolean = false,
    val error: String? = null,
)
