package io.github.stan2032.gravitygambit

import io.github.stan2032.gravitygambit.core.Gambit
import io.github.stan2032.gravitygambit.core.Objective
import io.github.stan2032.gravitygambit.core.ProjectileType
import io.github.stan2032.gravitygambit.core.SystemGenerator
import io.github.stan2032.gravitygambit.core.Tuning
import io.github.stan2032.gravitygambit.core.GameMode
import io.github.stan2032.gravitygambit.core.MatchRecord
import io.github.stan2032.gravitygambit.core.Seeds
import io.github.stan2032.gravitygambit.core.ShareCodec
import io.github.stan2032.gravitygambit.core.Vec2
import io.github.stan2032.gravitygambit.ui.GameLaunch
import io.github.stan2032.gravitygambit.ui.game.MatchController
import io.github.stan2032.gravitygambit.ui.game.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MatchController is the app module's most intricate logic: the phase state
 * machine, the incoming-replay queue, turn ownership on imported codes, and
 * code emission. It's a plain class (Compose state works fine on the JVM), so
 * it is tested here without a device.
 */
class MatchControllerTest {

    private fun drive(c: MatchController, maxFrames: Int = 4000) {
        var frames = 0
        while (c.phase == Phase.FLYING && frames++ < maxFrames) {
            c.advanceBy(4)
        }
        if (c.phase == Phase.RESOLVING) c.postResolve()
    }

    @Test
    fun campaignMatchProgressesToSummary() {
        val launch = GameLaunch(
            mode = GameMode.CAMPAIGN,
            seed = Seeds.campaign(0, 0),
            difficulty = 0, sector = 0, level = 0,
        )
        val c = MatchController.create(launch)!!
        assertEquals(Phase.AIMING, c.phase)

        var guard = 0
        while (c.phase != Phase.OVER && guard++ < 10) {
            c.fire()
            drive(c)
        }
        assertEquals(Phase.OVER, c.phase)
        assertNotNull("summary must exist once over", c.summary)
        assertTrue("campaign is capped at 4 shots", c.engine.shotsUsed <= 4)
    }

    @Test
    fun codeDuelRoundTripsThroughController() {
        // Player A creates a duel and fires once.
        val seed = Seeds.duelRandom(4242L)
        val a = MatchController.create(
            GameLaunch(mode = GameMode.DUEL, seed = seed, difficulty = Seeds.DUEL_DIFFICULTY, hotseat = false)
        )!!
        assertEquals(0, a.localPlayer)
        a.fire()
        drive(a)
        // Not A's turn now, so the controller emits a challenge code.
        val code = a.duelCode
        assertNotNull("a code must be emitted after the opponent's turn begins", code)

        // Player B imports it: verification passes, A's shot replays, B aims.
        val b = MatchController.create(
            GameLaunch(
                mode = GameMode.DUEL, seed = seed, difficulty = Seeds.DUEL_DIFFICULTY,
                hotseat = false, importedCode = code,
            )
        )!!
        assertEquals("importer plays as the second seat", 1, b.localPlayer)
        assertEquals("imported code replays the incoming shot first", Phase.FLYING, b.phase)
        assertTrue(b.isReplaying)

        drive(b)
        assertEquals(Phase.AIMING, b.phase)
        assertTrue("after the replay it is the importer's turn", b.isMyTurn)
        // Both devices agree on the state produced by that first shot.
        assertEquals(a.engine.hp.toList(), b.engine.hp.toList())
        assertEquals(a.engine.combinedHash, b.engine.combinedHash)
    }

    @Test
    fun tamperedCodeIsRefused() {
        val seed = Seeds.duelRandom(99L)
        val a = MatchController.create(
            GameLaunch(mode = GameMode.DUEL, seed = seed, difficulty = Seeds.DUEL_DIFFICULTY)
        )!!
        a.fire()
        drive(a)
        val honest = ShareCodec.decode(a.duelCode!!)!!
        val forged = ShareCodec.encode(
            MatchRecord(honest.mode, honest.difficulty, honest.seed, honest.turns, honest.hash + 1)
        )
        assertNull(
            "a forged hash must not produce a playable match",
            MatchController.create(
                GameLaunch(
                    mode = GameMode.DUEL, seed = seed, difficulty = Seeds.DUEL_DIFFICULTY,
                    importedCode = forged,
                )
            ),
        )
    }

    @Test
    fun hotseatAlternatesWithHandoff() {
        val c = MatchController.create(
            GameLaunch(
                mode = GameMode.DUEL, seed = Seeds.duelRandom(7L),
                difficulty = Seeds.DUEL_DIFFICULTY, hotseat = true,
            )
        )!!
        c.fire()
        drive(c)
        assertEquals("hot-seat pauses for the phone to change hands", Phase.HANDOFF, c.phase)
        assertNotNull(c.handoffText)
        assertNull("hot-seat play never emits codes", c.duelCode)
        c.continueFromHandoff()
        assertEquals(Phase.AIMING, c.phase)
        assertEquals(1, c.engine.currentShooter)
    }

    @Test
    fun aimNudgesStayInRange() {
        val c = MatchController.create(
            GameLaunch(mode = GameMode.CAMPAIGN, seed = Seeds.campaign(0, 1), difficulty = 0)
        )!!
        c.aimAngleDeci = 0
        c.nudgeAngle(-1)
        assertEquals("angle wraps rather than going negative", 3599, c.aimAngleDeci)
        c.aimAngleDeci = 3599
        c.nudgeAngle(1)
        assertEquals(0, c.aimAngleDeci)
        c.aimPower = 0
        c.nudgePower(-100)
        assertEquals(0, c.aimPower)
        c.aimPower = 1000
        c.nudgePower(100)
        assertEquals(1000, c.aimPower)
    }

    /**
     * The aim gesture must be *relative* to wherever the finger landed. If it
     * were anchored to the station, aiming would be pinned to one screen
     * corner — the bug this replaced. Two drags with identical displacement
     * from different origins must therefore produce identical aim.
     */
    @Test
    fun aimDragIsRelativeToTheTouchPoint() {
        val c = MatchController.create(
            GameLaunch(mode = GameMode.CAMPAIGN, seed = Seeds.campaign(0, 2), difficulty = 0)
        )!!

        // Pull straight down from the origin -> the shot heads straight up.
        c.beginAimDrag(Vec2(500.0, 500.0))
        c.updateAimDrag(Vec2(500.0, 500.0 + MatchController.AIM_DEAD_ZONE + 150.0))
        val angleA = c.aimAngleDeci
        val powerA = c.aimPower
        c.endAimDrag()

        c.beginAimDrag(Vec2(120.0, 1300.0))
        c.updateAimDrag(Vec2(120.0, 1300.0 + MatchController.AIM_DEAD_ZONE + 150.0))
        assertEquals("same displacement, far-away origin, same aim", angleA, c.aimAngleDeci)
        assertEquals(powerA, c.aimPower)
        c.endAimDrag()

        // 2700 deci-degrees = -90° in screen space = up. Pull down, fly up.
        assertEquals(2700, angleA)
        assertEquals(150.0 / MatchController.AIM_FULL_PULL * 1000, powerA.toDouble(), 1.0)
    }

    @Test
    fun aimDragDeadZoneDoesNotSteerAndStickClearsOnRelease() {
        val c = MatchController.create(
            GameLaunch(mode = GameMode.CAMPAIGN, seed = Seeds.campaign(0, 3), difficulty = 0)
        )!!
        c.aimAngleDeci = 1234
        c.aimPower = 500

        c.beginAimDrag(Vec2(400.0, 400.0))
        c.updateAimDrag(Vec2(400.0, 400.0 + MatchController.AIM_DEAD_ZONE - 1.0))
        assertEquals("inside the dead zone the aim is untouched", 1234, c.aimAngleDeci)
        assertEquals(500, c.aimPower)
        assertNotNull("the stick is still drawn while held", c.dragOrigin)

        c.endAimDrag()
        assertNull("the stick disappears on release", c.dragOrigin)
        assertNull(c.dragCurrent)
    }

    /**
     * A declaration must survive the whole presentation path: chosen before
     * firing, carried into the engine, judged there, and reset for the next
     * shot so it can never become a standing setting.
     */
    @Test
    fun declaringAGambitRidesWithTheShotAndResetsAfterIt() {
        val c = MatchController.create(
            GameLaunch(mode = GameMode.CAMPAIGN, seed = Seeds.campaign(1, 1), difficulty = 2)
        )!!
        assertTrue("a curated level must offer something to declare", c.gambitChoices.isNotEmpty())
        assertEquals(Gambit.NONE, c.selectedGambit)

        // Declare the one the arena proved it can pay, then fire.
        val choice = c.gambitChoices.first()
        c.selectedGambit = choice
        c.fire()
        drive(c)

        assertEquals(
            "the declaration reached the committed turn",
            choice.id, c.engine.turns[0].gambitId,
        )
        assertNotNull("the shot was judged against it", c.lastGambitKept)
        if (c.phase != Phase.OVER) {
            assertEquals("and it does not carry into the next shot", Gambit.NONE, c.selectedGambit)
        }
    }

    /**
     * A seat is only ever offered declarations the generator proved that seat
     * can honour — never the full menu.
     */
    @Test
    fun offeredGambitsComeFromTheArenaNotTheEnum() {
        val c = MatchController.create(
            GameLaunch(mode = GameMode.CAMPAIGN, seed = Seeds.campaign(2, 0), difficulty = 4)
        )!!
        assertEquals(
            c.spec.gambitOptionsFor(c.engine.currentShooter),
            c.gambitChoices,
        )
        for (g in c.gambitChoices) assertTrue(g in Gambit.declarable)
    }

    /**
     * A Capture system must be winnable through the whole presentation path,
     * not merely in the engine: the shell has to be issued, the brake has to
     * be reachable, and holding the ring has to end the match as a win.
     */
    @Test
    fun aCaptureSystemIsWinnableThroughTheController() {
        // Find a generated capture system the same way the campaign would.
        var launch: GameLaunch? = null
        var spec: io.github.stan2032.gravitygambit.core.SystemSpec? = null
        outer@ for (sector in 0 until 6) {
            for (level in 0 until 12) {
                val d = Seeds.campaignDifficulty(sector, level)
                val s = SystemGenerator.generate(Seeds.campaign(sector, level), d, GameMode.CAMPAIGN)
                if (s.objective == Objective.CAPTURE) {
                    spec = s
                    launch = GameLaunch(
                        mode = GameMode.CAMPAIGN, seed = Seeds.campaign(sector, level),
                        difficulty = d, sector = sector, level = level,
                    )
                    break@outer
                }
            }
        }
        assertNotNull("no capture system generated — the objective is dead", launch)

        val c = MatchController.create(launch!!)!!
        assertEquals(Objective.CAPTURE, c.objective)
        assertEquals(
            "the brake must be reachable or the system cannot be won",
            ProjectileType.RETRO.id, c.effectiveType,
        )

        // Find the winning inputs with bare simulations first — cheap — then
        // drive only that one through the controller. Brute-forcing the whole
        // grid through the real per-tick playback path took 56 seconds for an
        // assertion that needs exactly one solution.
        var solution: Triple<Int, Int, Int>? = null
        outer2@ for (angle in 0 until 3600 step SystemGenerator.CAPTURE_ANGLE_STEP) {
            for (power in SystemGenerator.CAPTURE_POWERS) {
                for (burn in SystemGenerator.CAPTURE_BURN_TICKS) {
                    val r = io.github.stan2032.gravitygambit.core.LiveShot(
                        spec!!.bodies, spec.stations, 0, angle, power,
                        ProjectileType.RETRO.id, presetBrakeTick = burn,
                        maxTicks = Tuning.SOLVER_MAX_TICKS, markedBody = spec.markedBody,
                    ).runToCompletion()
                    if (r.captureDwell >= Tuning.CAPTURE_TICKS) {
                        solution = Triple(angle, power, burn); break@outer2
                    }
                }
            }
        }
        assertNotNull("the generator proved this winnable but nothing wins it", solution)

        val (angle, power, burn) = solution!!
        c.aimAngleDeci = angle
        c.aimPower = power
        c.fire()
        var frames = 0
        while (c.phase == Phase.FLYING && frames++ < 4000) {
            if (c.liveShot?.tick == burn) c.brakeNow()
            c.advanceBy(1)
        }
        if (c.phase == Phase.RESOLVING) c.postResolve()
        assertTrue("a proved-winnable capture system was not winnable in play", c.engine.campaignWon)
    }

    /**
     * Camera shake is presentation, and must stay that way: it is a pure
     * function of animation progress, it decays to nothing well before the
     * explosion ends, and nothing about it can reach the simulation.
     */
    @Test
    fun cameraShakeIsBoundedDecayingAndPurelyVisual() {
        val e = io.github.stan2032.gravitygambit.ui.game.Explosion(
            Vec2(500.0, 800.0), big = true, seed = 7L,
        )
        val cap = io.github.stan2032.gravitygambit.ui.game.Explosion.BIG_SHAKE
        var previousPeak = Double.MAX_VALUE
        var p = 0f
        while (p <= 1f) {
            val x = e.shakeX(p)
            val y = e.shakeY(p)
            assertTrue("shake left the board at p=$p", kotlin.math.abs(x) <= cap)
            assertTrue("shake left the board at p=$p", kotlin.math.abs(y) <= cap)
            p += 0.01f
        }
        assertEquals(
            "shake must be over before the explosion is",
            0.0,
            e.shakeX(io.github.stan2032.gravitygambit.ui.game.Explosion.SHAKE_FADE),
            1e-9,
        )
        assertEquals(0.0, e.shakeX(1f), 1e-9)

        // Pure function of progress: the same input twice gives the same shake,
        // so a replay never diverges visually either.
        assertEquals(e.shakeX(0.2f), e.shakeX(0.2f), 0.0)
        // And two explosions do not shake identically.
        val other = io.github.stan2032.gravitygambit.ui.game.Explosion(
            Vec2(500.0, 800.0), big = true, seed = 8L,
        )
        assertTrue(
            "different explosions should not share a shake phase",
            kotlin.math.abs(e.shakeX(0.1f) - other.shakeX(0.1f)) > 1e-9,
        )
        previousPeak.let { }
    }

    @Test
    fun feedbackCodeIsVerifiableMidMatch() {
        val c = MatchController.create(
            GameLaunch(mode = GameMode.CAMPAIGN, seed = Seeds.campaign(1, 2), difficulty = 2)
        )!!
        c.fire()
        drive(c)
        // A bug report's attached code must survive verification, otherwise the
        // triage pipeline (feedback/README.md) can't reproduce what the player saw.
        val verified = ShareCodec.decodeAndVerify(c.feedbackCode())
        assertNotNull("feedback replay code must verify", verified)
        assertEquals(c.engine.turns, verified!!.record.turns)
    }
}
