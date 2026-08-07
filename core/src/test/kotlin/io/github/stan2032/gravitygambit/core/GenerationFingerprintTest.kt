package io.github.stan2032.gravitygambit.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A **golden fingerprint of what every seed generates** — the guard that was
 * missing, and without which no generator change can be called safe.
 *
 * The protocol depends on generation being *stable across versions*, not
 * merely self-consistent within a run. A campaign replay code carries
 * `(mode, difficulty, seed, turns, hash)` and the receiver **regenerates the
 * board from the seed** (`ShareCodec.decodeAndVerify`). So if generation ever
 * produces a different layout for the same seed, every code in the wild stops
 * verifying — the exact failure a protocol version exists to announce.
 *
 * What guarded that until 0.19.5 was `generationIsDeterministic`, which
 * asserts `generate(12345L, 3) == generate(12345L, 3)`. That is determinism
 * *within one run, on one seed, at one difficulty*, and it would pass happily
 * if the generator were rewritten to produce completely different levels. It
 * is the "a guard only checks what is enumerated in it" defect (D-43) applied
 * to the most protocol-sensitive code in the repository.
 *
 * This fingerprints the whole generated spec — geometry, masses, kinds,
 * objective, marked body, gates, and the measured solver values — across a
 * fixed spread of seeds, difficulties and modes, and pins it to a constant.
 *
 * **If this test fails, generation output changed.** That is not necessarily
 * a bug, but it is *never* a silent one: either revert the change, or bump
 * `ShareCodec.VERSION` deliberately and update the constant below in the same
 * commit, saying why in DECISIONS.md. Do not "fix" this by pasting the new
 * number in — that turns a protocol break into a rounding error.
 */
class GenerationFingerprintTest {

    /**
     * FNV-1a over the fields that define a board. Doubles go in by raw bits:
     * the determinism contract (D-4) already guarantees these are bit-stable,
     * so anything less exact would hide the changes worth catching.
     */
    private class Fold {
        var h: Long = LiveShot.FNV_OFFSET
        fun put(v: Long) {
            var x = v
            repeat(8) {
                h = (h xor (x and 0xFF)) * LiveShot.FNV_PRIME
                x = x ushr 8
            }
        }
        fun put(d: Double) = put(java.lang.Double.doubleToRawLongBits(d))
        fun put(i: Int) = put(i.toLong())
        fun put(b: Boolean) = put(if (b) 1L else 0L)
    }

    private fun fingerprint(spec: SystemSpec, f: Fold) {
        f.put(spec.seed)
        f.put(spec.difficulty)
        f.put(spec.name.hashCode())
        f.put(spec.bodies.size)
        for (b in spec.bodies) {
            f.put(b.x); f.put(b.y); f.put(b.radius); f.put(b.mass)
            f.put(b.kind.ordinal); f.put(b.palette); f.put(b.vx); f.put(b.vy)
        }
        f.put(spec.stations.size)
        for (s in spec.stations) { f.put(s.x); f.put(s.y); f.put(s.owner) }
        f.put(spec.solverHitRate)
        f.put(spec.anchorLeverage)
        for (m in spec.gambitMasks) f.put(m)
        f.put(spec.objective.ordinal)
        f.put(spec.markedBody)
        f.put(spec.gates.size)
        for (g in spec.gates) { f.put(g.x1); f.put(g.y1); f.put(g.x2); f.put(g.y2) }
    }

    @Test
    fun everySeedStillGeneratesExactlyTheBoardItAlwaysHas() {
        val f = Fold()
        // A fixed spread: several difficulties (which change body counts,
        // objectives and proof paths), plus the daily and duel generators,
        // which take different routes through the same code.
        for (difficulty in intArrayOf(0, 2, 4, 6, 8, 11)) {
            for (level in 0 until 3) {
                fingerprint(
                    SystemGenerator.generate(
                        Seeds.campaign(difficulty, level), difficulty, GameMode.CAMPAIGN,
                    ),
                    f,
                )
            }
        }
        for (day in 0L until 3L) {
            fingerprint(
                SystemGenerator.generate(
                    Seeds.daily(Seeds.LAUNCH_EPOCH_DAY + day), 5, GameMode.DAILY,
                ),
                f,
            )
        }
        for (entropy in 0L until 3L) {
            fingerprint(
                SystemGenerator.generate(
                    Seeds.duelRandom(entropy), Seeds.DUEL_DIFFICULTY, GameMode.DUEL,
                ),
                f,
            )
        }

        println("generation fingerprint: ${f.h}")
        assertEquals(
            "GENERATION OUTPUT CHANGED. Every share code regenerates its board from the seed, " +
                "so this breaks verification for every code in the wild. Either revert, or bump " +
                "ShareCodec.VERSION deliberately and update this constant in the same commit " +
                "with a DECISIONS.md entry. Do not just paste the new number in.",
            FINGERPRINT, f.h,
        )
    }

    private companion object {
        /**
         * Captured 2026-08-07 at protocol v9 (0.19.5). Changing this value is
         * a protocol decision, not a test fix — see the class note.
         */
        const val FINGERPRINT = 6258813215614024784L
    }
}
