package io.github.stan2032.gravitygambit.core

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MEASUREMENT — what a field-shaping mutator would cost, given that
 * `MutatorReachTest` proves it cannot be built the obvious way.
 *
 * The obvious way is to hand the generator a parameter ("make this one
 * denser"). That is closed: a share code carries only
 * `(mode, difficulty, seed, turns, hash)`, so a receiver regenerates the
 * unmutated board and the leg's own replay code fails to verify.
 *
 * The way that is still open is **seed selection**: do not ask the generator
 * for anything, search its natural output for a seed that already satisfies
 * the condition, and offer that. The code then carries a seed whose plain
 * regeneration reproduces exactly the board that was played, so nothing about
 * verification changes. It also answers D-24 for free — the search predicate
 * *is* the proof that the mutator took, and a search that finds nothing simply
 * does not offer the mutator, which is the honest failure.
 *
 * What it costs is the open question, and it is the reason this is a
 * measurement rather than a feature.
 */
class MutatorSearchTest {

    /** A candidate "dense field": more bodies than the difficulty usually gives. */
    private fun isDense(spec: SystemSpec): Boolean = spec.bodies.size >= DENSE_BODIES

    private companion object {
        const val DENSE_BODIES = 7
        const val SEARCH_BUDGET = 24
    }

    @Test
    fun measureWhatAFieldShapingMutatorWouldCost() {
        println("=== natural body counts, which decide what a mutator can even ask for ===")
        for (d in intArrayOf(2, 4, 6, 8, 11)) {
            val counts = HashMap<Int, Int>()
            repeat(60) { i ->
                val s = SystemGenerator.generate(
                    Seeds.expedition(99L, i, d % 3), d, GameMode.CAMPAIGN,
                )
                counts[s.bodies.size] = (counts[s.bodies.size] ?: 0) + 1
            }
            val dense = counts.filterKeys { it >= DENSE_BODIES }.values.sum()
            println("d=$d  histogram(n=60): ${counts.toSortedMap()}   >=$DENSE_BODIES bodies: $dense/60")
        }

        println("=== cost of searching for a dense field by seed selection ===")
        for (d in intArrayOf(4, 8, 11)) {
            var tries = 0
            var found = -1
            val t0 = System.nanoTime()
            while (tries < SEARCH_BUDGET && found < 0) {
                val s = SystemGenerator.generate(
                    Seeds.expedition(7L, tries, 1), d, GameMode.CAMPAIGN,
                )
                if (isDense(s)) found = s.bodies.size
                tries++
            }
            val ms = (System.nanoTime() - t0) / 1_000_000
            println(
                "d=$d  tries=$tries  " +
                    (if (found > 0) "found $found bodies" else "NONE within $SEARCH_BUDGET") +
                    "  total ${ms}ms (desktop; phones run 3-6x slower)",
            )
        }
        println("=== conclusion is recorded in D-44, not inferred from these lines ===")
    }

    /**
     * The property that makes seed selection *safe*, as opposed to merely
     * affordable: a mutator built this way changes nothing about verification,
     * because the seed it picks is an ordinary seed and its board is that
     * seed's ordinary output.
     */
    @Test
    fun aSeedSelectedMutatorLegStillVerifies() {
        var chosen: SystemSpec? = null
        var tries = 0
        while (tries < SEARCH_BUDGET && chosen == null) {
            val s = SystemGenerator.generate(Seeds.expedition(7L, tries, 1), 11, GameMode.CAMPAIGN)
            if (isDense(s)) chosen = s
            tries++
        }
        val spec = chosen ?: return  // nothing dense in budget; the honest no-op
        val engine = MatchEngine(spec, GameMode.CAMPAIGN)
        var angle = 200
        while (!engine.over) {
            engine.commit(
                engine.beginShot(angle, 640, ProjectileType.STANDARD.id).runToCompletion(),
            )
            angle += 370
        }
        val code = ShareCodec.encode(
            MatchRecord(
                mode = GameMode.CAMPAIGN.id,
                difficulty = spec.difficulty,
                seed = spec.seed,
                turns = engine.turns.toList(),
                hash = engine.combinedHash,
            ),
        )
        assertTrue(
            "a seed-selected dense leg must verify like any other — that is the whole point " +
                "of choosing a seed instead of asking the generator for a favour",
            ShareCodec.decodeAndVerify(code) != null,
        )
    }
}
