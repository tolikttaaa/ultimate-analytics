package com.ttaaa.ultimate.domain

import java.util.UUID

enum class DrillKind { DRILL, GAME, WARMUP, REST }

/** A user-defined category for segments; metrics are aggregated across sessions by drill type (spec 3, 5). */
data class DrillType(
    val id: UUID,
    /** Stable identifier such as `CUTTING_1V1`: upper case letters, digits and `_`. */
    val code: String,
    val name: String,
    /** Display colour, `#RRGGBB`. */
    val color: String,
    val kind: DrillKind,
) {
    init {
        require(CODE.matches(code)) { "Drill type code must use A-Z, 0-9 and _: $code" }
        require(name.isNotBlank()) { "Drill type name must not be blank" }
        require(COLOR.matches(color)) { "Drill type colour must be #RRGGBB: $color" }
    }

    private companion object {
        val CODE = Regex("[A-Z0-9_]+")
        val COLOR = Regex("#[0-9A-Fa-f]{6}")
    }
}
