package com.ttaaa.ultimate.analysis.smoothing

import kotlin.math.abs
import kotlin.math.pow

/**
 * Savitzky–Golay filter: a least-squares polynomial of degree [order] fitted to a window of [size] samples 1 s apart.
 * The fitted value and slope at any position of the window are weighted sums of the window's samples.
 */
class SavitzkyGolay(val size: Int, val order: Int) {

    init {
        require(size >= 1 && order in 0..<size) { "Need 0 <= order < size, got order $order, size $size" }
    }

    private val valueWeights = Array(size) { weights(size, order, derivative = 0, position = it) }
    private val slopeWeights = Array(size) { weights(size, order, derivative = 1, position = it) }

    /** Fitted value at [position] of the window starting at [start] in [samples]. */
    fun value(samples: DoubleArray, start: Int, position: Int): Double = dot(valueWeights[position], samples, start)

    /** Fitted first derivative (per second) at [position] of the window starting at [start] in [samples]. */
    fun slope(samples: DoubleArray, start: Int, position: Int): Double = dot(slopeWeights[position], samples, start)

    companion object {

        /**
         * Weights `w` such that `Σ w[j] · y[j]` over a window of [size] samples is the [derivative]-th derivative,
         * at window index [position], of the degree-[order] least-squares polynomial through the samples.
         */
        fun weights(size: Int, order: Int, derivative: Int, position: Int): DoubleArray {
            require(size >= 1 && order in 0..<size) { "Need 0 <= order < size, got order $order, size $size" }
            require(derivative >= 0 && position in 0..<size) { "Invalid derivative $derivative or position $position" }
            val center = (size - 1) / 2.0
            val z = DoubleArray(size) { it - center }
            val terms = order + 1
            // Polynomial y(z) = Σ β_k z^k; normal equations (AᵀA) β = Aᵀ y with A[j][k] = z_j^k.
            val normal = Array(terms) { r -> DoubleArray(terms) { c -> z.sumOf { it.pow(r + c) } } }
            // The derivative at z0 is e · β with e_k = d^n/dz^n z^k at z0, so the weights are Aᵀ (AᵀA)⁻¹ e.
            val z0 = position - center
            val e = DoubleArray(terms) { k ->
                if (k < derivative) 0.0 else fallingFactorial(k, derivative) * z0.pow(k - derivative)
            }
            val u = solve(normal, e)
            return DoubleArray(size) { j -> (0..<terms).sumOf { k -> u[k] * z[j].pow(k) } }
        }

        private fun fallingFactorial(n: Int, k: Int): Double = (0..<k).fold(1.0) { product, i -> product * (n - i) }

        /** Solves `m x = b` by Gaussian elimination with partial pivoting; `m` is small and well conditioned. */
        private fun solve(m: Array<DoubleArray>, b: DoubleArray): DoubleArray {
            val n = b.size
            val a = Array(n) { m[it].copyOf() }
            val x = b.copyOf()
            for (col in 0..<n) {
                val pivot = (col..<n).maxBy { abs(a[it][col]) }
                a[col] = a[pivot].also { a[pivot] = a[col] }
                x[col] = x[pivot].also { x[pivot] = x[col] }
                for (row in col + 1..<n) {
                    val factor = a[row][col] / a[col][col]
                    for (c in col..<n) a[row][c] -= factor * a[col][c]
                    x[row] -= factor * x[col]
                }
            }
            for (row in n - 1 downTo 0) {
                x[row] = (x[row] - (row + 1..<n).sumOf { a[row][it] * x[it] }) / a[row][row]
            }
            return x
        }

        private fun dot(weights: DoubleArray, samples: DoubleArray, start: Int): Double {
            var sum = 0.0
            for (j in weights.indices) sum += weights[j] * samples[start + j]
            return sum
        }
    }
}
