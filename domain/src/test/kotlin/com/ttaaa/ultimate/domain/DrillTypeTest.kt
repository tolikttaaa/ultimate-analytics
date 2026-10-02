package com.ttaaa.ultimate.domain

import io.kotest.assertions.throwables.shouldThrow
import org.junit.jupiter.api.Test
import java.util.UUID

class DrillTypeTest {

    private fun drillType(code: String = "CUTTING_1V1", name: String = "Cutting 1v1", color: String = "#2E7D32") =
        DrillType(UUID.randomUUID(), code, name, color, DrillKind.DRILL)

    @Test
    fun `validates code, name and colour`() {
        drillType()
        shouldThrow<IllegalArgumentException> { drillType(code = "cutting") }
        shouldThrow<IllegalArgumentException> { drillType(code = "") }
        shouldThrow<IllegalArgumentException> { drillType(name = " ") }
        shouldThrow<IllegalArgumentException> { drillType(color = "green") }
        shouldThrow<IllegalArgumentException> { drillType(color = "#2E7D3") }
    }
}
