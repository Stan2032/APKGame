package io.github.stan2032.gravitygambit.core

/**
 * SplitMix64 — a tiny, fast, high-quality deterministic PRNG.
 *
 * We own this implementation (rather than using java.util.Random or kotlin.random)
 * so that the exact sequence is guaranteed stable across JVM versions, Android
 * versions and future library updates. Determinism is a gameplay contract in this
 * game: the same seed must generate the same star system on every device, forever.
 */
class Rng(seed: Long) {
    private var state: Long = seed

    fun nextLong(): Long {
        state += GOLDEN
        var z = state
        z = (z xor (z ushr 30)) * MIX1
        z = (z xor (z ushr 27)) * MIX2
        return z xor (z ushr 31)
    }

    /** Uniform double in [0, 1). */
    fun nextDouble(): Double = (nextLong() ushr 11) * DOUBLE_UNIT

    /** Uniform int in [0, bound). bound must be > 0. */
    fun nextInt(bound: Int): Int = ((nextLong() ushr 33) % bound).toInt()

    fun range(min: Double, max: Double): Double = min + nextDouble() * (max - min)

    fun chance(p: Double): Boolean = nextDouble() < p

    companion object {
        private const val DOUBLE_UNIT = 1.0 / (1L shl 53)
        private val GOLDEN = 0x9E3779B97F4A7C15UL.toLong()
        private val MIX1 = 0xBF58476D1CE4E5B9UL.toLong()
        private val MIX2 = 0x94D049BB133111EBUL.toLong()

        /** Deterministic 64-bit mix of two values; used to derive sub-seeds. */
        fun mix(a: Long, b: Long): Long {
            var z = a xor (b * GOLDEN)
            z = (z xor (z ushr 30)) * MIX1
            z = (z xor (z ushr 27)) * MIX2
            return z xor (z ushr 31)
        }
    }
}
