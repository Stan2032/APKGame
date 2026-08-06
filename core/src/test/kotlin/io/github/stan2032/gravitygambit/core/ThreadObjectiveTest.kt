package io.github.stan2032.gravitygambit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Thread: fly through the gates **in order**. The order is the whole objective,
 * so most of these tests are about rejecting flights that touched every gate
 * but not as a sequence.
 */
class ThreadObjectiveTest {

    /** Open range with three gates straddling a straight vertical shot. */
    private fun laneSpec(gates: List<Gate>): SystemSpec = SystemSpec(
        seed = 31L,
        difficulty = 4,
        name = "Thread Range",
        bodies = listOf(Body(160.0, 800.0, 34.0, 1.0e5, BodyKind.PLANET, 2)),
        stations = listOf(
            Station(Tuning.WORLD_W / 2, Tuning.WORLD_H - 150.0, 0),
            Station(Tuning.WORLD_W / 2, 150.0, 1),
        ),
        solverHitRate = 0.1,
        objectiveId = Objective.THREAD.id,
        gates = gates,
    )

    /** A horizontal gate at height [y], centred on the firing lane. */
    private fun gateAt(y: Double, cx: Double = Tuning.WORLD_W / 2): Gate =
        Gate(cx - Tuning.GATE_HALF_WIDTH, y, cx + Tuning.GATE_HALF_WIDTH, y)

    private fun straightUp(spec: SystemSpec): ShotResult =
        LiveShot(
            spec.bodies, spec.stations, 0, 2700, 900, ProjectileType.STANDARD.id,
            maxTicks = Tuning.SOLVER_MAX_TICKS, gates = spec.gates,
        ).runToCompletion()

    @Test
    fun aFlightThroughAllGatesInOrderWins() {
        // Ascending shot, so gates are met bottom-up: 1200, then 900, then 600.
        val spec = laneSpec(listOf(gateAt(1200.0), gateAt(900.0), gateAt(600.0)))
        val r = straightUp(spec)
        assertEquals("all three gates crossed", 3, r.gatesPassed)

        val e = MatchEngine(spec, GameMode.CAMPAIGN)
        e.commit(r)
        assertTrue("threading every gate wins the system", e.campaignWon)
    }

    /**
     * The load-bearing one. Same flight, same gates, listed in the order the
     * shot will *not* meet them. It physically passes through all three and
     * must still have threaded nothing.
     */
    @Test
    fun theSameFlightInTheWrongOrderThreadsNothing() {
        val spec = laneSpec(listOf(gateAt(600.0), gateAt(900.0), gateAt(1200.0)))
        val r = straightUp(spec)
        assertTrue(
            "expected the flight to stall on the first gate it cannot reach yet, " +
                "got ${r.gatesPassed}",
            r.gatesPassed < 3,
        )
        val e = MatchEngine(spec, GameMode.CAMPAIGN)
        e.commit(r)
        assertFalse("crossing gates out of order is not threading", e.campaignWon)
    }

    @Test
    fun aFlightThatMissesTheGapDoesNotCount() {
        // Gates shifted far to the side of the firing lane.
        val spec = laneSpec(
            listOf(gateAt(1200.0, cx = 120.0), gateAt(900.0, cx = 120.0), gateAt(600.0, cx = 120.0))
        )
        val r = straightUp(spec)
        assertEquals("a shot up the middle passes no offset gate", 0, r.gatesPassed)
    }

    @Test
    fun partialProgressIsNotAWin() {
        val spec = laneSpec(listOf(gateAt(1200.0), gateAt(900.0), gateAt(120.0)))
        val r = straightUp(spec)
        assertTrue("expected two of three", r.gatesPassed in 1..2)
        val e = MatchEngine(spec, GameMode.CAMPAIGN)
        e.commit(r)
        assertFalse("two gates out of three is not a thread", e.campaignWon)
    }

    /** Gate crossings are folded into the hash, so a replay agrees on them. */
    @Test
    fun gateProgressSurvivesReplay() {
        val spec = laneSpec(listOf(gateAt(1200.0), gateAt(900.0), gateAt(600.0)))
        val live = MatchEngine(spec, GameMode.CAMPAIGN)
        live.commit(live.beginShot(2700, 900, ProjectileType.STANDARD.id).runToCompletion())
        val rebuilt = MatchReplayer.rebuild(spec, live.turns, GameMode.CAMPAIGN)
        assertEquals(live.combinedHash, rebuilt.verifiedHash)
        assertEquals(live.campaignWon, rebuilt.engine.campaignWon)
        assertEquals(
            live.results[0].gatesPassed,
            rebuilt.engine.results[0].gatesPassed,
        )
    }

    /**
     * D-27 again: gates are *built* from a flight the generator already flew,
     * but that is not the claim being made. The claim is that a player can
     * find a path, so every shipped Thread system is re-proved independently.
     */
    @Test
    fun everyGeneratedThreadSystemIsProvablyThreadable() {
        var found = 0
        for (sector in 0 until 6) {
            for (level in 0 until 12 step 2) {
                val spec = SystemGenerator.generate(
                    Seeds.campaign(sector, level),
                    Seeds.campaignDifficulty(sector, level),
                    GameMode.CAMPAIGN,
                )
                if (spec.objective != Objective.THREAD) continue
                found++
                assertEquals(
                    "a Thread system must carry its gates",
                    SystemGenerator.THREAD_GATES, spec.gates.size,
                )
                assertTrue(
                    "s$sector.l$level asks for a thread it cannot pay",
                    SystemGenerator.proveThread(spec),
                )
            }
        }
        assertTrue("no thread systems generated at all", found > 0)
    }
}
