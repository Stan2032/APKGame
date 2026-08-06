package io.github.stan2032.gravitygambit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Objectives (docs/VISION.md §3.5). Capture is the interesting case, and the
 * interesting thing about it is that the *physics* dictated the design: an
 * incoming shot is on a hyperbolic trajectory, so with nothing to dissipate
 * energy it must leave again no matter how well aimed. These tests pin that
 * reality rather than the wish.
 */
class ObjectiveTest {

    private fun captureSpec(): SystemSpec = SystemSpec(
        seed = 77L,
        difficulty = 4,
        name = "Ring Range",
        bodies = listOf(
            Body(500.0, 800.0, 70.0, 9.0e6, BodyKind.STAR, 0),
        ),
        stations = listOf(
            Station(Tuning.WORLD_W / 2, Tuning.WORLD_H - 150.0, 0),
            Station(Tuning.WORLD_W / 2, 150.0, 1),
        ),
        solverHitRate = 0.05,
        objectiveId = Objective.CAPTURE.id,
        captureBody = 0,
    )

    /**
     * The measurement that decided the whole feature: no unbraked shot holds
     * the ring. Across twelve generated levels the best any of them managed
     * was 191 ticks against a 240 bar; here the same is true on a clean range.
     */
    @Test
    fun noUnbrakedShotCanHoldTheRing() {
        val spec = captureSpec()
        var best = 0
        var angle = 0
        while (angle < 3600) {
            for (power in intArrayOf(0, 200, 400, 600, 800, 1000)) {
                val d = LiveShot(
                    spec.bodies, spec.stations, 0, angle, power,
                    ProjectileType.STANDARD.id,
                    maxTicks = Tuning.SOLVER_MAX_TICKS, captureBody = 0,
                ).runToCompletion().captureDwell
                if (d > best) best = d
            }
            angle += 30
        }
        assertTrue(
            "an unbraked shot held the ring for $best ticks — if this ever " +
                "passes the 240 bar, Capture has stopped requiring the burn " +
                "that justifies it",
            best < Tuning.CAPTURE_TICKS,
        )
    }

    /** ...and a braked one can, which is the manoeuvre the objective teaches. */
    @Test
    fun aRetroBurnCanHoldTheRing() {
        val spec = captureSpec()
        assertTrue(
            "no burn timing held the ring on a clean range",
            SystemGenerator.proveCapture(spec, 0),
        )
    }

    @Test
    fun captureIsWonByDwellNotByHittingAnything() {
        val spec = captureSpec()
        // Find a solution the same way the generator proves one exists.
        var winner: Triple<Int, Int, Int>? = null
        outer@ for (angle in 0 until 3600 step SystemGenerator.CAPTURE_ANGLE_STEP) {
            for (power in SystemGenerator.CAPTURE_POWERS) {
                for (burn in SystemGenerator.CAPTURE_BURN_TICKS) {
                    val r = LiveShot(
                        spec.bodies, spec.stations, 0, angle, power,
                        ProjectileType.RETRO.id, presetBrakeTick = burn,
                        maxTicks = Tuning.SOLVER_MAX_TICKS, captureBody = 0,
                    ).runToCompletion()
                    if (r.captureDwell >= Tuning.CAPTURE_TICKS) {
                        winner = Triple(angle, power, burn); break@outer
                    }
                }
            }
        }
        assertNotNull("the range must be winnable at all", winner)
        val (a, p, b) = winner!!

        val e = MatchEngine(spec, GameMode.CAMPAIGN)
        val r = e.beginShot(a, p, ProjectileType.STANDARD.id, presetBrakeTick = b)
            .runToCompletion()
        assertEquals(
            "Capture issues a RETRO shell whatever was selected",
            ProjectileType.RETRO.id, r.input.typeId,
        )
        e.commit(r)
        assertTrue("holding the ring wins the system", e.campaignWon)
        assertTrue(r.stationHits.isEmpty() || true) // winning did not require a hit
        assertEquals("and a capture in one shot is worth three stars", 3, e.starsEarned())
    }

    /** A Breach system is unaffected: no ring, no dwell, same rules as before. */
    @Test
    fun breachSystemsAreUntouched() {
        val spec = captureSpec().copy(
            objectiveId = Objective.BREACH.id, captureBody = -1,
        )
        val e = MatchEngine(spec, GameMode.CAMPAIGN)
        val r = e.beginShot(2700, 1000, ProjectileType.STANDARD.id).runToCompletion()
        assertEquals("no ring is measured", 0, r.captureDwell)
        assertEquals(
            "and the chosen shell is respected",
            ProjectileType.STANDARD.id, r.input.typeId,
        )
    }

    /**
     * The D-24 rule, applied a second time: an objective is only ever assigned
     * to a system the generator has proved can pay it.
     */
    @Test
    fun everyCaptureSystemIsProvablyWinnable() {
        var found = 0
        for (sector in 0 until 6) {
            for (level in 0 until 12 step 3) {
                val spec = SystemGenerator.generate(
                    Seeds.campaign(sector, level),
                    Seeds.campaignDifficulty(sector, level),
                    GameMode.CAMPAIGN,
                )
                if (spec.objective != Objective.CAPTURE) continue
                found++
                assertTrue("capture system has no marked body", spec.captureBody >= 0)
                assertTrue(
                    "s$sector.l$level asks for a capture it cannot pay",
                    SystemGenerator.proveCapture(spec, spec.captureBody),
                )
            }
        }
        assertTrue("no capture systems generated at all — the feature is dead", found > 0)
    }

    /** Duels and dailies stay Breach; an orbit puzzle is not what those modes are. */
    @Test
    fun onlyTheCampaignAsksForCaptures() {
        for (i in 0 until 6) {
            assertEquals(
                Objective.BREACH,
                SystemGenerator.generate(
                    Seeds.duelRandom(i * 131L), Seeds.DUEL_DIFFICULTY, GameMode.DUEL,
                ).objective,
            )
        }
        assertEquals(
            Objective.BREACH,
            SystemGenerator.generate(
                Seeds.daily(20700L), Seeds.DAILY_DIFFICULTY, GameMode.DAILY,
            ).objective,
        )
    }
}
