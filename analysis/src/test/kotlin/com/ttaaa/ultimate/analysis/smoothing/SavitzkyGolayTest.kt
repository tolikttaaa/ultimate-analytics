package com.ttaaa.ultimate.analysis.smoothing

import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.numericDouble
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.pow

class SavitzkyGolayTest {

    private fun weights(size: Int, order: Int, derivative: Int, position: Int) =
        SavitzkyGolay.weights(size, order, derivative, position).toList()

    private fun ratios(vararg numerators: Int, denominator: Int) = numerators.map { it.toDouble() / denominator }

    private infix fun List<Double>.shouldBeCloseTo(expected: List<Double>) {
        size shouldBe expected.size
        zip(expected).forEach { (actual, wanted) -> actual shouldBe (wanted plusOrMinus 1e-12) }
    }

    @Test
    fun `matches the classic centred smoothing coefficients`() {
        weights(5, 2, 0, 2) shouldBeCloseTo ratios(-3, 12, 17, 12, -3, denominator = 35)
        weights(7, 2, 0, 3) shouldBeCloseTo ratios(-2, 3, 6, 7, 6, 3, -2, denominator = 21)
        weights(7, 4, 0, 3) shouldBeCloseTo ratios(5, -30, 75, 131, 75, -30, 5, denominator = 231)
    }

    @Test
    fun `matches the classic centred first-derivative coefficients`() {
        weights(5, 2, 1, 2) shouldBeCloseTo ratios(-2, -1, 0, 1, 2, denominator = 10)
        weights(5, 3, 1, 2) shouldBeCloseTo ratios(1, -8, 0, 8, -1, denominator = 12)
    }

    @Test
    fun `matches the off-centre coefficients at the window edge`() {
        weights(5, 2, 0, 0) shouldBeCloseTo ratios(31, 9, -3, -5, 3, denominator = 35)
        weights(5, 2, 1, 0) shouldBeCloseTo ratios(-54, 13, 40, 27, -26, denominator = 70)
    }

    @Test
    fun `reproduces every polynomial up to its order exactly`() = runTest {
        val cases = arbitrary {
            val size = Arb.int(1..11).bind()
            val order = Arb.int(0..minOf(4, size - 1)).bind()
            val coefficients = Arb.list(Arb.numericDouble(-5.0, 5.0), order + 1..order + 1).bind()
            val position = Arb.int(0..<size).bind()
            Triple(SavitzkyGolay(size, order), coefficients, position)
        }

        checkAll(cases) { (filter, coefficients, position) ->
            fun polynomial(x: Double) = coefficients.withIndex().sumOf { (k, c) -> c * x.pow(k) }
            fun derivative(x: Double) = coefficients.withIndex().drop(1).sumOf { (k, c) -> k * c * x.pow(k - 1) }
            val samples = DoubleArray(filter.size) { polynomial(it.toDouble()) }
            val x = position.toDouble()

            filter.value(samples, 0, position) shouldBe (polynomial(x) plusOrMinus 1e-8 * (1 + abs(polynomial(x))))
            filter.slope(samples, 0, position) shouldBe (derivative(x) plusOrMinus 1e-8 * (1 + abs(derivative(x))))
        }
    }
}
