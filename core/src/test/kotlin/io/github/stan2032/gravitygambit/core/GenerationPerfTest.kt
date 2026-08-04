package io.github.stan2032.gravitygambit.core

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Generation-cost guardrail. Level start is the one place the player waits on
 * computation (docs/PERFORMANCE.md), so worst-case generation is measured, not
 * assumed. Desktop JVM numbers are a proxy: phones run roughly 3-6x slower, so
 * the desktop budget below is set correspondingly tight.
 *
 * These assertions are deliberately loose enough not to flake on a loaded CI
 * runner while still catching an order-of-magnitude regression.
 */
class GenerationPerfTest {

    private fun timeMs(block: () -> Unit): Long {
        val t0 = System.nanoTime()
        block()
        return (System.nanoTime() - t0) / 1_000_000
    }

    @Test
    fun generationStaysWithinBudgetAcrossDifficulties() {
        // Warm the JIT so the measurement reflects steady state, as on device.
        repeat(3) { SystemGenerator.generate(Seeds.campaign(0, it), 2, GameMode.CAMPAIGN) }

        val worst = ArrayList<Pair<Int, Long>>()
        for (difficulty in intArrayOf(0, 3, 6, 9, 11)) {
            var maxMs = 0L
            repeat(4) { i ->
                val ms = timeMs {
                    SystemGenerator.generate(Seeds.campaign(difficulty, i), difficulty, GameMode.CAMPAIGN)
                }
                if (ms > maxMs) maxMs = ms
            }
            worst.add(difficulty to maxMs)
        }
        println("generation worst-case ms by difficulty: $worst")
        for ((difficulty, ms) in worst) {
            assertTrue(
                "difficulty $difficulty generation took ${ms}ms (desktop budget 1200ms)",
                ms < 1200,
            )
        }
    }

    @Test
    fun aimPreviewIsCheapEnoughForDragRate() {
        // Preview runs per drag event; at 120Hz touch sampling it must cost far
        // less than a frame. Worst case: max preview tier + splitting shot.
        val spec = SystemGenerator.generate(Seeds.campaign(3, 0), 6, GameMode.CAMPAIGN)
        repeat(20) {
            Preview.trajectories(spec.bodies, spec.stations, 0, 2700, 800, ProjectileType.SPLITTER.id, 270)
        }
        val runs = 200
        val ms = timeMs {
            repeat(runs) { i ->
                Preview.trajectories(
                    spec.bodies, spec.stations, 0, 2000 + i, 800, ProjectileType.SPLITTER.id, 270,
                )
            }
        }
        val perCall = ms.toDouble() / runs
        println("aim preview cost: ${"%.3f".format(perCall)}ms/call (worst tier, splitter)")
        assertTrue("preview at ${perCall}ms/call is too expensive for drag rate", perCall < 2.0)
    }

    @Test
    fun duelGenerationStaysWithinBudget() {
        repeat(2) { SystemGenerator.generate(Seeds.duelRandom(it.toLong()), 5, GameMode.DUEL) }
        var maxMs = 0L
        repeat(4) { i ->
            val ms = timeMs {
                SystemGenerator.generate(Seeds.duelRandom(100L + i), Seeds.DUEL_DIFFICULTY, GameMode.DUEL)
            }
            if (ms > maxMs) maxMs = ms
        }
        println("duel generation worst-case ms: $maxMs")
        // Duels solve from both stations, so they cost ~2x a campaign level.
        assertTrue("duel generation took ${maxMs}ms (desktop budget 2000ms)", maxMs < 2000)
    }
}
