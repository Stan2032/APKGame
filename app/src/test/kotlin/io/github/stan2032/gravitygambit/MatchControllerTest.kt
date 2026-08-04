package io.github.stan2032.gravitygambit

import io.github.stan2032.gravitygambit.core.GameMode
import io.github.stan2032.gravitygambit.core.MatchRecord
import io.github.stan2032.gravitygambit.core.Seeds
import io.github.stan2032.gravitygambit.core.ShareCodec
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
