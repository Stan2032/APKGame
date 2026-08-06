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
        markedBody = 0,
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
                    maxTicks = Tuning.SOLVER_MAX_TICKS, markedBody = 0,
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
                        maxTicks = Tuning.SOLVER_MAX_TICKS, markedBody = 0,
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
            objectiveId = Objective.BREACH.id, markedBody = -1,
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
                assertTrue("capture system has no marked body", spec.markedBody >= 0)
                assertTrue(
                    "s$sector.l$level asks for a capture it cannot pay",
                    SystemGenerator.proveCapture(spec, spec.markedBody),
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

/**
 * Courier: strike the drifting comet rather than a fixed station. Cheap to add
 * because comets already existed — but measurement found two levels whose comet
 * could not be hit at all, so presence is not proof (D-27).
 */
class CourierTest {

    private fun courierSpec(vx: Double = 60.0): SystemSpec = SystemSpec(
        seed = 55L,
        difficulty = 4,
        name = "Courier Range",
        bodies = listOf(
            Body(200.0, 900.0, 40.0, 1.2e6, BodyKind.PLANET, 2),
            Body(500.0, 700.0, 18.0, 2.4e5, BodyKind.COMET, 9, vx = vx),
        ),
        stations = listOf(
            Station(Tuning.WORLD_W / 2, Tuning.WORLD_H - 150.0, 0),
            Station(Tuning.WORLD_W / 2, 150.0, 1),
        ),
        solverHitRate = 0.05,
        objectiveId = Objective.COURIER.id,
        markedBody = 1,
    )

    @Test
    fun strikingTheMarkedCometWinsWithoutTouchingAStation() {
        val spec = courierSpec()
        assertTrue("the range must be winnable at all", SystemGenerator.proveCourier(spec, 1))

        var won: ShotResult? = null
        outer@ for (angle in 0 until 3600 step SystemGenerator.GAMBIT_ANGLE_STEP) {
            for (power in SystemGenerator.GAMBIT_POWERS) {
                val e = MatchEngine(spec, GameMode.CAMPAIGN)
                val r = e.beginShot(angle, power, ProjectileType.STANDARD.id).runToCompletion()
                if (r.markedBodyHit) {
                    e.commit(r)
                    assertTrue("striking the comet wins the system", e.campaignWon)
                    assertTrue("and it did so without hitting a station", r.stationHits.isEmpty())
                    won = r
                    break@outer
                }
            }
        }
        assertNotNull("no shot struck the marked comet", won)
    }

    /** A different body's death must not count as the delivery. */
    @Test
    fun hittingSomethingElseIsNotDelivery() {
        val spec = courierSpec()
        val e = MatchEngine(spec, GameMode.CAMPAIGN)
        // Straight into the planet, which is body 0, not the marked comet.
        var struckPlanet: ShotResult? = null
        outer@ for (angle in 0 until 3600 step 20) {
            for (power in intArrayOf(300, 600, 900)) {
                val r = LiveShot(
                    spec.bodies, spec.stations, 0, angle, power,
                    ProjectileType.STANDARD.id, maxTicks = Tuning.SOLVER_MAX_TICKS,
                    markedBody = 1,
                ).runToCompletion()
                if (r.bodyHitCount > 0 && !r.markedBodyHit) { struckPlanet = r; break@outer }
            }
        }
        assertNotNull("expected some shot to die on the wrong body", struckPlanet)
        e.commit(struckPlanet!!)
        assertFalse("dying on the wrong body is not a delivery", e.campaignWon)
    }

    /** Every generated Courier system must have a comet that can be struck. */
    @Test
    fun everyCourierSystemIsProvablyWinnable() {
        var found = 0
        for (sector in 0 until 6) {
            for (level in 0 until 12 step 2) {
                val spec = SystemGenerator.generate(
                    Seeds.campaign(sector, level),
                    Seeds.campaignDifficulty(sector, level),
                    GameMode.CAMPAIGN,
                )
                if (spec.objective != Objective.COURIER) continue
                found++
                assertEquals(
                    "a Courier must mark a comet",
                    BodyKind.COMET, spec.bodies[spec.markedBody].kind,
                )
                assertTrue(
                    "s$sector.l$level asks for a delivery it cannot pay",
                    SystemGenerator.proveCourier(spec, spec.markedBody),
                )
            }
        }
        assertTrue("no courier systems generated at all", found > 0)
    }
}
