package com.ttaaa.ultimate.domain

/** Playing surface of a session or a geozone (spec 3). */
enum class Surface { GRASS, SAND, UNKNOWN }

/**
 * Where [Session.surface] comes from. A [MANUAL] surface is never overwritten by geozone matching
 * (spec 5, invariant 4); [NONE] goes with [Surface.UNKNOWN] when no geozone matches (spec 6.6).
 */
enum class SurfaceSource { GEOZONE, MANUAL, NONE }
